package com.kds.menu.auth.controller;

import com.kds.menu.auth.dto.EstadoRequest;
import com.kds.menu.auth.dto.RolRequest;
import com.kds.menu.auth.dto.UsuarioResponse;
import com.kds.menu.auth.exception.NoAutenticadoException;
import com.kds.menu.auth.security.UsuarioAutenticado;
import com.kds.menu.auth.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/usuarios")
@Tag(name = "Usuarios", description = "Gestión de cuentas, roles y estados (solo ADMIN)")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    @Operation(summary = "Listar todas las cuentas, opcionalmente filtradas")
    public List<UsuarioResponse> listar(
            @RequestParam(name = "q", required = false) String busqueda) {
        return usuarioService.listar(busqueda);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar una cuenta por id")
    public UsuarioResponse obtener(@PathVariable Long id) {
        return usuarioService.obtener(id);
    }

    @PatchMapping("/{id}/rol")
    @Operation(summary = "Asignar o cambiar el rol de una cuenta")
    public UsuarioResponse asignarRol(
            @PathVariable Long id,
            @Valid @RequestBody RolRequest request) {
        return usuarioService.asignarRol(id, request);
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Bloquear o desbloquear una cuenta")
    public UsuarioResponse cambiarEstado(
            @PathVariable Long id,
            @Valid @RequestBody EstadoRequest request,
            @AuthenticationPrincipal UsuarioAutenticado admin) {
        if (admin == null) {
            throw new NoAutenticadoException();
        }
        return usuarioService.cambiarEstado(id, request, admin.id());
    }
}
