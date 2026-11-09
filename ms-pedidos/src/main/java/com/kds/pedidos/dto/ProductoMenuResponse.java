package com.kds.pedidos.dto;

import java.math.BigDecimal;

/**
 * Proyección del producto expuesto por ms-menu (GET /productos/{id}).
 */
public record ProductoMenuResponse(
        Long id,
        String nombre,
        BigDecimal precio,
        Boolean disponible
) {
}
