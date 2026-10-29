package com.kds.pedidos.dto;

import com.kds.pedidos.model.EstadoPedido;
import java.time.LocalDateTime;

public record PedidoEstadoActualizadoEvent(
        String eventoId,
        String evento,
        Long pedidoId,
        String numeroPedido,
        EstadoPedido estado,
        LocalDateTime fechaActualizacion
) {}