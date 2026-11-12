package com.kds.pedidos.dto;

import com.kds.pedidos.model.EstadoPedido;

public record NotificacionCaja(
        String tipo,
        Long pedidoId,
        String numeroPedido,
        EstadoPedido estado
) {}
