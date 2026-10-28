package com.kds.cocina.dto;

import com.kds.cocina.model.EstadoOrden;
import jakarta.validation.constraints.NotNull;

public record ActualizarEstadoRequest(
        @NotNull(message = "El estado es obligatorio")
        EstadoOrden estado) {
}
