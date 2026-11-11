package com.kds.menu.auth.controller;

import com.kds.menu.auth.dto.AuthResponse;
import com.kds.menu.auth.dto.LoginRequest;
import com.kds.menu.auth.dto.RegistroRequest;
import com.kds.menu.auth.dto.UsuarioResponse;
import com.kds.menu.auth.exception.NoAutenticadoException;
import com.kds.menu.auth.security.UsuarioAutenticado;
import com.kds.menu.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Tag(name = "Autenticación", description = "Registro, login y sesión del usuario")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar una cuenta pública pendiente de rol")
    public UsuarioResponse registrar(@Valid @RequestBody RegistroRequest request) {
        return authService.registrar(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión y obtener un JWT Bearer")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Consultar el usuario y estado actuales desde la base de datos")
    public UsuarioResponse me(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        if (usuario == null) {
            throw new NoAutenticadoException();
        }
        return authService.obtenerActual(usuario.id());
    }
}
