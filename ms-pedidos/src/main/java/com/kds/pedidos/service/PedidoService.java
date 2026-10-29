package com.kds.pedidos.service;

import com.kds.pedidos.config.RabbitMQConfig;
import com.kds.pedidos.dto.*;
import com.kds.pedidos.exception.RecursoNoEncontradoException;
import com.kds.pedidos.messaging.PedidosEventPublisher;
import com.kds.pedidos.model.DetallePedido;
import com.kds.pedidos.model.Pedido;
import com.kds.pedidos.repository.PedidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class PedidoService {

    private static final Logger log = LoggerFactory.getLogger(PedidoService.class);
    public static final String TOPIC_PEDIDOS = "/topic/pedidos";
    public static final String TIPO_ESTADO_ACTUALIZADO = "ESTADO_ACTUALIZADO";

    private final PedidoRepository pedidoRepository;
    private final PedidosEventPublisher eventPublisher;
    private final SimpMessagingTemplate messagingTemplate;

    public PedidoService(
            PedidoRepository pedidoRepository,
            PedidosEventPublisher eventPublisher,
            SimpMessagingTemplate messagingTemplate) {
        this.pedidoRepository = pedidoRepository;
        this.eventPublisher = eventPublisher;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional(readOnly = true)
    public List<PedidoResponse> listarPedidos() {
        return pedidoRepository.findAllByOrderByFechaCreacionDesc().stream()
                .map(this::aPedidoResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PedidoResponse obtenerPedido(Long id) {
        return aPedidoResponse(buscarPedidoPorId(id));
    }

    @Transactional(readOnly = true)
    public PedidoEstadoResponse obtenerEstadoPedido(Long id) {
        Pedido pedido = buscarPedidoPorId(id);
        return new PedidoEstadoResponse(pedido.getId(), pedido.getNumeroPedido(), pedido.getEstado());
    }

    @Transactional
    public PedidoResponse registrarPedido(CrearPedidoRequest request) {
        LocalDateTime ahora = LocalDateTime.now();
        Pedido pedido = new Pedido();
        
        // Simulación de generación correlativa de tickets
        long timestampSec = System.currentTimeMillis() % 100000;
        pedido.setNumeroPedido(String.format("ORD-%05d", timestampSec));
        pedido.setFechaCreacion(ahora);
        pedido.setFechaActualizacion(ahora);

        for (var itemReq : request.items()) {
            DetallePedido detalle = new DetallePedido();
            detalle.setProductoId(itemReq.productoId());
            
            // Simulación de datos provenientes de ms-menu (Regla 6.2 y 11)
            // En un entorno productivo completo se llamaría a ms-menu mediante un cliente REST (Feign)
            Map<String, Object> mockMenuData = simularConsultaMenu(itemReq.productoId());
            detalle.setNombreProducto((String) mockMenuData.get("nombre"));
            detalle.setPrecioUnitario((BigDecimal) mockMenuData.get("precio"));
            detalle.setCantidad(itemReq.cantidad());
            detalle.calcularSubtotal();
            
            pedido.agregarDetalle(detalle);
        }

        Pedido guardado = pedidoRepository.save(pedido);
        PedidoResponse respuesta = aPedidoResponse(guardado);

        // Desacoplamiento asíncrono seguro: Emisión solo tras persistencia física en BD
        List<PedidoCreadoEvent.ItemPedidoCreado> itemsEvento = guardado.getDetalles().stream()
                .map(d -> new PedidoCreadoEvent.ItemPedidoCreado(d.getProductoId(), d.getNombreProducto(), d.getCantidad()))
                .toList();

        PedidoCreadoEvent evento = new PedidoCreadoEvent(
                UUID.randomUUID().toString(),
                RabbitMQConfig.RK_PEDIDO_CREADO,
                guardado.getId(),
                guardado.getNumeroPedido(),
                guardado.getFechaCreacion(),
                itemsEvento
        );

        ejecutarTrasCommit(() -> eventPublisher.publicarPedidoCreado(evento));
        log.info("Pedido persistido localmente: id={}, ticket={}", guardado.getId(), guardado.getNumeroPedido());

        return respuesta;
    }

    @Transactional
    public void sincronizarEstadoCocina(PedidoEstadoActualizadoEvent evento) {
        pedidoRepository.findById(evento.pedidoId()).ifPresentOrElse(pedido -> {
            pedido.setEstado(evento.estado());
            pedido.setFechaActualizacion(evento.fechaActualizacion());
            pedidoRepository.save(pedido);
            log.info("Pedido sincronizado desde RabbitMQ: id={}, estado={}", pedido.getId(), pedido.getEstado());

            // Notificación reactiva inmediata a cajas/pantallas de despacho front-end
            NotificacionCaja notificacion = new NotificacionCaja(
                    TIPO_ESTADO_ACTUALIZADO,
                    pedido.getId(),
                    pedido.getNumeroPedido(),
                    pedido.getEstado()
            );
            ejecutarTrasCommit(() -> messagingTemplate.convertAndSend(TOPIC_PEDIDOS, notificacion));
        }, () -> log.error("No se encontró el pedidoId={} para la actualización asíncrona", evento.pedidoId()));
    }

    private void ejecutarTrasCommit(Runnable accion) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    accion.run();
                }
            });
        } else {
            accion.run();
        }
    }

    private Pedido buscarPedidoPorId(Long id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pedido no localizado con id " + id));
    }

    private PedidoResponse aPedidoResponse(Pedido p) {
        return new PedidoResponse(p.getId(), p.getNumeroPedido(), p.getEstado(), p.getTotal(), p.getFechaCreacion());
    }

    private Map<String, Object> simularConsultaMenu(Long productoId) {
        // Mapeo defensivo para simular datos del microservicio ms-menu según guía técnica
        if (productoId == 1L) {
            return Map.of("nombre", "Hamburguesa Clasica", "precio", new BigDecimal("4.50"));
        } else if (productoId == 5L) {
            return Map.of("nombre", "Papas", "precio", new BigDecimal("2.25"));
        }
        return Map.of("nombre", "Producto Genérico KDS", "precio", new BigDecimal("3.00"));
    }
}
