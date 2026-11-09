package com.kds.cocina.controller;

import com.kds.cocina.dto.ActualizarEstadoRequest;
import com.kds.cocina.dto.OrdenCocinaResponse;
import com.kds.cocina.service.OrdenCocinaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/ordenes")
@Tag(name = "Ordenes de cocina", description = "Consulta y preparacion de ordenes")
public class OrdenCocinaController {

    private final OrdenCocinaService ordenCocinaService;

    public OrdenCocinaController(OrdenCocinaService ordenCocinaService) {
        this.ordenCocinaService = ordenCocinaService;
    }

    @GetMapping
    @Operation(summary = "Listar ordenes de cocina")
    public List<OrdenCocinaResponse> listar() {
        return ordenCocinaService.listarOrdenes();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar una orden de cocina")
    public OrdenCocinaResponse obtener(@PathVariable Long id) {
        return ordenCocinaService.obtenerOrden(id);
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Cambiar el estado de preparacion de una orden")
    public OrdenCocinaResponse cambiarEstado(
            @PathVariable Long id,
            @Valid @RequestBody ActualizarEstadoRequest request) {
        return ordenCocinaService.actualizarEstado(id, request.estado());
    }
}
