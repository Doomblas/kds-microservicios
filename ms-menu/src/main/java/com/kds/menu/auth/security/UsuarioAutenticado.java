package com.kds.menu.auth.security;

/**
 * Principal autenticado a partir del JWT. No es una entidad JPA: los datos
 * sensibles siempre se leen de nuevo desde la base en /auth/me y /usuarios.
 */
public record UsuarioAutenticado(
        Long id,
        String email,
        String rol,
        String estado) {
}
