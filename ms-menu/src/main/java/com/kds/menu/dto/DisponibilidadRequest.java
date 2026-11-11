package com.kds.menu.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Datos para actualizar unicamente la disponibilidad de un producto")
public class DisponibilidadRequest {

    @Schema(
            description = "Nuevo estado de disponibilidad del producto",
            example = "false",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private Boolean disponible;

    public Boolean getDisponible() {
        return disponible;
    }

    public void setDisponible(Boolean disponible) {
        this.disponible = disponible;
    }
}
