package com.kds.cocina.dto;

import com.kds.cocina.model.EstadoOrden;

import java.time.LocalDateTime;

/**
 * Contrato v1 del evento pedido.estado.actualizado publicado por ms-display-cocina.
 */
public record PedidoEstadoActualizadoEvent(
        String eventoId,
        String evento,
        Long pedidoId,
        String numeroPedido,
        EstadoOrden estado,
        LocalDateTime fechaActualizacion) {
}
