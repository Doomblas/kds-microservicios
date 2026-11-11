package com.kds.menu.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Datos para crear o actualizar un producto")
public class ProductoRequest {

    @Schema(
            description = "Nombre del producto",
            example = "Hamburguesa Clasica",
            maxLength = 120,
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    @Size(max = 120)
    private String nombre;

    @Schema(
            description = "Descripcion del producto",
            example = "Carne, queso, lechuga y salsa",
            maxLength = 255)
    @Size(max = 255)
    private String descripcion;

    @Schema(
            description = "Precio unitario del producto",
            example = "4.50",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    @Positive
    private BigDecimal precio;

    @Schema(
            description = "Indica si el producto esta disponible",
            example = "true",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private Boolean disponible;

    @Schema(
            description = "Identificador de la categoria a la que pertenece el producto",
            example = "1",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    @Positive
    private Long categoriaId;

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public BigDecimal getPrecio() {
        return precio;
    }

    public void setPrecio(BigDecimal precio) {
        this.precio = precio;
    }

    public Boolean getDisponible() {
        return disponible;
    }

    public void setDisponible(Boolean disponible) {
        this.disponible = disponible;
    }

    public Long getCategoriaId() {
        return categoriaId;
    }

    public void setCategoriaId(Long categoriaId) {
        this.categoriaId = categoriaId;
    }
}