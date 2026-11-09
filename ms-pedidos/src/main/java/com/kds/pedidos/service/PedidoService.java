package com.kds.pedidos.service;

import com.kds.pedidos.client.MenuClient;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PedidoService {

    private static final Logger log = LoggerFactory.getLogger(PedidoService.class);
    public static final String TOPIC_PEDIDOS = "/topic/pedidos";
    public static final String TIPO_ESTADO_ACTUALIZADO = "ESTADO_ACTUALIZADO";

    private final PedidoRepository pedidoRepository;
    private final PedidosEventPublisher eventPublisher;
    private final SimpMessagingTemplate messagingTemplate;
    private final MenuClient menuClient;

    public PedidoService(
            PedidoRepository pedidoRepository,
            PedidosEventPublisher eventPublisher,
            SimpMessagingTemplate messagingTemplate,
            MenuClient menuClient) {
        this.pedidoRepository = pedidoRepository;
        this.eventPublisher = eventPublisher;
        this.messagingTemplate = messagingTemplate;
        this.menuClient = menuClient;
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

        // Ticket único: millis completo evita colisiones del módulo % 100000
        pedido.setNumeroPedido("ORD-" + System.currentTimeMillis());
        pedido.setFechaCreacion(ahora);
        pedido.setFechaActualizacion(ahora);

        for (var itemReq : request.items()) {
            ProductoMenuResponse producto = menuClient.obtenerProducto(itemReq.productoId());

            DetallePedido detalle = new DetallePedido();
            detalle.setProductoId(producto.id());
            detalle.setNombreProducto(producto.nombre());
            detalle.setPrecioUnitario(producto.precio());
            detalle.setCantidad(itemReq.cantidad());
            detalle.calcularSubtotal();

            pedido.agregarDetalle(detalle);
        }

        Pedido guardado = pedidoRepository.save(pedido);
        PedidoResponse respuesta = aPedidoResponse(guardado);

        List<PedidoCreadoEvent.ItemPedidoCreado> itemsEvento = guardado.getDetalles().stream()
                .map(d -> new PedidoCreadoEvent.ItemPedidoCreado(
                        d.getProductoId(), d.getNombreProducto(), d.getCantidad()))
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
}
