package com.kds.pedidos.dto;

import com.kds.pedidos.model.EstadoPedido;

public record PedidoEstadoResponse(
        Long id,
        String numeroPedido,
        EstadoPedido estado
) {}
