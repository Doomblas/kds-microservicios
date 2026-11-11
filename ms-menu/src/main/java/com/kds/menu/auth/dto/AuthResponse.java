package com.kds.menu.auth.dto;

public record AuthResponse(
        String token,
        String tipo,
        long expiraEnSegundos,
        UsuarioResponse usuario) {
}
