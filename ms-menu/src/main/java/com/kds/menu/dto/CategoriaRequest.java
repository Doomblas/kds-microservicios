package com.kds.menu.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos para crear una categoria")
public class CategoriaRequest {

    @Schema(
            description = "Nombre unico de la categoria",
            example = "Hamburguesas",
            maxLength = 100,
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    @Size(max = 100)
    private String nombre;

    @Schema(
            description = "Indica si la categoria esta activa",
            example = "true",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private Boolean activo;

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public Boolean getActivo() {
        return activo;
    }

    public void setActivo(Boolean activo) {
        this.activo = activo;
    }
}
