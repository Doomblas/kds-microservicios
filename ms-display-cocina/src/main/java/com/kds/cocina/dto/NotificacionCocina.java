package com.kds.cocina.dto;

import com.kds.cocina.model.EstadoOrden;

import java.util.List;

/**
 * Mensaje enviado al canal WebSocket /topic/cocina.
 */
public record NotificacionCocina(
        String tipo,
        Long pedidoId,
        String numeroPedido,
        EstadoOrden estado,
        List<ItemNotificacion> items) {

    public record ItemNotificacion(
            String nombreProducto,
            Integer cantidad) {
    }
}
