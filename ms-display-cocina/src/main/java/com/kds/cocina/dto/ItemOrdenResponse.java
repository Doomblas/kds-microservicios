package com.kds.cocina.dto;

public record ItemOrdenResponse(
        Long productoId,
        String nombreProducto,
        Integer cantidad) {
}
