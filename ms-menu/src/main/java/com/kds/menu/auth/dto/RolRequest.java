package com.kds.menu.auth.dto;

import com.kds.menu.auth.model.Rol;
import jakarta.validation.constraints.NotNull;

public record RolRequest(
        @NotNull(message = "El rol es obligatorio")
        Rol rol) {
}
