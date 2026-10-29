package com.kds.pedidos.dto;

import com.kds.pedidos.model.EstadoPedido;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PedidoResponse(
        Long id,
        String numeroPedido,
        EstadoPedido estado,
        BigDecimal total,
        LocalDateTime fechaCreacion
) {}
