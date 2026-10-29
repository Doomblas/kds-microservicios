package com.kds.pedidos.dto;

import java.time.LocalDateTime;
import java.util.List;

public record PedidoCreadoEvent(
        String eventoId,
        String evento,
        Long pedidoId,
        String numeroPedido,
        LocalDateTime fechaCreacion,
        List<ItemPedidoCreado> items
) {
    public record ItemPedidoCreado(
            Long productoId,
            String nombreProducto,
            Integer cantidad
    ) {}
}
