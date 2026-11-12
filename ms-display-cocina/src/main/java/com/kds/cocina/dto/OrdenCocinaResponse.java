package com.kds.cocina.dto;

import com.kds.cocina.model.EstadoOrden;

import java.time.LocalDateTime;
import java.util.List;

public record OrdenCocinaResponse(
        Long id,
        Long pedidoId,
        String numeroPedido,
        EstadoOrden estado,
        LocalDateTime fechaRecepcion,
        LocalDateTime fechaInicio,
        LocalDateTime fechaFinalizacion,
        List<ItemOrdenResponse> items) {
}
