package com.kds.cocina.service;

import com.kds.cocina.dto.ItemOrdenResponse;
import com.kds.cocina.dto.OrdenCocinaResponse;
import com.kds.cocina.dto.PedidoCreadoEvent;
import com.kds.cocina.exception.RecursoNoEncontradoException;
import com.kds.cocina.exception.TransicionEstadoInvalidaException;
import com.kds.cocina.messaging.CocinaEventPublisher;
import com.kds.cocina.model.EstadoOrden;
import com.kds.cocina.model.ItemCocina;
import com.kds.cocina.model.OrdenCocina;
import com.kds.cocina.repository.OrdenCocinaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class OrdenCocinaService {

    private static final Logger log = LoggerFactory.getLogger(OrdenCocinaService.class);

    private final OrdenCocinaRepository ordenCocinaRepository;
    private final CocinaEventPublisher eventPublisher;

    public OrdenCocinaService(
            OrdenCocinaRepository ordenCocinaRepository,
            CocinaEventPublisher eventPublisher) {
        this.ordenCocinaRepository = ordenCocinaRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<OrdenCocinaResponse> listarOrdenes() {
        return ordenCocinaRepository.findAllByOrderByFechaRecepcionAsc().stream()
                .map(this::aRespuesta)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrdenCocinaResponse obtenerOrden(Long id) {
        return aRespuesta(buscarOrden(id));
    }

    /**
     * Registra en cocina_db la orden recibida en pedido.creado.
     * El pedido_id es unico, por lo que un evento duplicado no genera otra orden
     * ni una segunda notificacion WebSocket.
     */
    @Transactional
    public OrdenCocinaResponse registrarPedido(PedidoCreadoEvent evento) {
        validarEvento(evento);

        var existente = ordenCocinaRepository.findByPedidoId(evento.pedidoId());
        if (existente.isPresent()) {
            log.warn("Evento duplicado ignorado: eventoId={}, pedidoId={}",
                    evento.eventoId(), evento.pedidoId());
            return aRespuesta(existente.get());
        }

        OrdenCocina orden = new OrdenCocina();
        orden.setPedidoId(evento.pedidoId());
        orden.setNumeroPedido(evento.numeroPedido());
        orden.setEstado(EstadoOrden.PENDIENTE);
        orden.setFechaRecepcion(evento.fechaCreacion() != null
                ? evento.fechaCreacion()
                : LocalDateTime.now());

        for (PedidoCreadoEvent.ItemPedidoCreado item : evento.items()) {
            ItemCocina itemCocina = new ItemCocina();
            itemCocina.setProductoId(item.productoId());
            itemCocina.setNombreProducto(item.nombreProducto());
            itemCocina.setCantidad(item.cantidad());
            orden.agregarItem(itemCocina);
        }

        OrdenCocina guardada = ordenCocinaRepository.save(orden);
        OrdenCocinaResponse respuesta = aRespuesta(guardada);

        notificarPedidoCreadoTrasCommit(respuesta);
        log.info("Orden de cocina creada: ordenId={}, pedidoId={}, numeroPedido={}",
                guardada.getId(), guardada.getPedidoId(), guardada.getNumeroPedido());

        return respuesta;
    }

    /**
     * Aplica el flujo PENDIENTE -> PREPARANDO -> LISTO. Un PATCH con el mismo
     * estado es idempotente y no vuelve a publicar el evento.
     */
    @Transactional
    public OrdenCocinaResponse actualizarEstado(Long id, EstadoOrden nuevoEstado) {
        OrdenCocina orden = buscarOrden(id);
        EstadoOrden estadoActual = orden.getEstado();

        if (estadoActual == nuevoEstado) {
            return aRespuesta(orden);
        }

        if (nuevoEstado == null || nuevoEstado == EstadoOrden.PENDIENTE) {
            throw new IllegalArgumentException(
                    "El estado PENDIENTE no puede asignarse manualmente");
        }

        if (!estadoActual.permiteTransicionA(nuevoEstado)) {
            throw new TransicionEstadoInvalidaException(
                    "Transicion de estado no permitida: " + estadoActual + " -> " + nuevoEstado);
        }

        LocalDateTime ahora = LocalDateTime.now();
        orden.setEstado(nuevoEstado);

        if (nuevoEstado == EstadoOrden.PREPARANDO) {
            orden.setFechaInicio(ahora);
        } else if (nuevoEstado == EstadoOrden.LISTO) {
            orden.setFechaFinalizacion(ahora);
        }

        OrdenCocina actualizada = ordenCocinaRepository.save(orden);
        OrdenCocinaResponse respuesta = aRespuesta(actualizada);

        publicarEstadoTrasCommit(respuesta, ahora);
        log.info("Estado de orden actualizado: ordenId={}, pedidoId={}, estado={}",
                actualizada.getId(), actualizada.getPedidoId(), actualizada.getEstado());

        return respuesta;
    }

    private void notificarPedidoCreadoTrasCommit(OrdenCocinaResponse respuesta) {
        ejecutarTrasCommit(() -> eventPublisher.notificarPedidoCreado(respuesta));
    }

    private void publicarEstadoTrasCommit(
            OrdenCocinaResponse respuesta, LocalDateTime fechaActualizacion) {
        ejecutarTrasCommit(() ->
                eventPublisher.publicarEstadoActualizado(respuesta, fechaActualizacion));
    }

    /**
     * Las notificaciones se emiten despues del commit para que el frontend no
     * consulte un estado que todavia no es visible en cocina_db. En pruebas
     * unitarias no hay transaccion activa y se ejecuta de inmediato.
     */
    private void ejecutarTrasCommit(Runnable accion) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            accion.run();
                        }
                    });
        } else {
            accion.run();
        }
    }

    private OrdenCocina buscarOrden(Long id) {
        return ordenCocinaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Orden de cocina no encontrada con id " + id));
    }

    private void validarEvento(PedidoCreadoEvent evento) {
        if (evento == null) {
            throw new IllegalArgumentException("El evento pedido.creado es obligatorio");
        }
        if (evento.eventoId() == null || evento.eventoId().isBlank()) {
            throw new IllegalArgumentException("eventoId es obligatorio");
        }
        if (evento.pedidoId() == null) {
            throw new IllegalArgumentException("pedidoId es obligatorio");
        }
        if (evento.numeroPedido() == null || evento.numeroPedido().isBlank()) {
            throw new IllegalArgumentException("numeroPedido es obligatorio");
        }
        if (evento.items() == null || evento.items().isEmpty()) {
            throw new IllegalArgumentException("pedido.creado debe incluir al menos un item");
        }

        for (PedidoCreadoEvent.ItemPedidoCreado item : evento.items()) {
            if (item == null || item.productoId() == null) {
                throw new IllegalArgumentException("productoId es obligatorio en cada item");
            }
            if (item.nombreProducto() == null || item.nombreProducto().isBlank()) {
                throw new IllegalArgumentException("nombreProducto es obligatorio en cada item");
            }
            if (item.cantidad() == null || item.cantidad() <= 0) {
                throw new IllegalArgumentException("cantidad debe ser mayor que cero");
            }
        }
    }

    private OrdenCocinaResponse aRespuesta(OrdenCocina orden) {
        List<ItemOrdenResponse> items = orden.getItems().stream()
                .filter(Objects::nonNull)
                .map(item -> new ItemOrdenResponse(
                        item.getProductoId(),
                        item.getNombreProducto(),
                        item.getCantidad()))
                .toList();

        return new OrdenCocinaResponse(
                orden.getId(),
                orden.getPedidoId(),
                orden.getNumeroPedido(),
                orden.getEstado(),
                orden.getFechaRecepcion(),
                orden.getFechaInicio(),
                orden.getFechaFinalizacion(),
                items);
    }
}
