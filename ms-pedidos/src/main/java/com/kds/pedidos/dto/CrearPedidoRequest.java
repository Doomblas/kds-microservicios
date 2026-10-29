package com.kds.pedidos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record CrearPedidoRequest(
        @NotEmpty(message = "El pedido debe incluir al menos un item")
        @Valid
        List<ItemPedidoRequest> items
) {
    public record ItemPedidoRequest(
            @NotNull(message = "productoId es obligatorio")
            Long productoId,
            
            @NotNull(message = "cantidad es obligatoria")
            @Positive(message = "la cantidad debe ser mayor que cero")
            Integer cantidad
    ) {}
}
