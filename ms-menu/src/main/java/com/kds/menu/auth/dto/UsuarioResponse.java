package com.kds.menu.auth.dto;

import com.kds.menu.auth.model.EstadoUsuario;
import com.kds.menu.auth.model.Rol;
import com.kds.menu.auth.model.Usuario;

import java.time.LocalDateTime;

/**
 * Proyección pública del usuario. No incluye passwordHash.
 */
public record UsuarioResponse(
        Long id,
        String nombre,
        String email,
        Rol rol,
        EstadoUsuario estado,
        LocalDateTime fechaCreacion) {

    public static UsuarioResponse desde(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getEmail(),
                usuario.getRol(),
                usuario.getEstado(),
                usuario.getFechaCreacion());
    }
}
