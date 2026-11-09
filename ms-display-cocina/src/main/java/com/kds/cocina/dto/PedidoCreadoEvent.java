package com.kds.cocina.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Contrato v1 del evento pedido.creado publicado por ms-pedidos.
 */
public record PedidoCreadoEvent(
        String eventoId,
        String evento,
        Long pedidoId,
        String numeroPedido,
        LocalDateTime fechaCreacion,
        List<ItemPedidoCreado> items) {

    public record ItemPedidoCreado(
            Long productoId,
            String nombreProducto,
            Integer cantidad) {
    }
}
