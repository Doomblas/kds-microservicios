package com.kds.menu.auth.dto;

import com.kds.menu.auth.model.EstadoUsuario;
import jakarta.validation.constraints.NotNull;

public record EstadoRequest(
        @NotNull(message = "El estado es obligatorio")
        EstadoUsuario estado) {
}
