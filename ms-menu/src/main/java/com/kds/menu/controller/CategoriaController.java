package com.kds.menu.controller;

import com.kds.menu.dto.ApiError;
import com.kds.menu.dto.CategoriaRequest;
import com.kds.menu.model.Categoria;
import com.kds.menu.service.CategoriaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Categorías", description = "Administración de categorías del menú")
@RestController
@RequestMapping("/categorias")
public class CategoriaController {

    private final CategoriaService categoriaService;

    public CategoriaController(CategoriaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    @Operation(
            summary = "Listar categorías",
            description = "Obtiene todas las categorías registradas en el menú.")
    @ApiResponse(responseCode = "200", description = "Listado de categorías")
    @GetMapping
    public List<Categoria> listarTodas() {
        return categoriaService.listarTodas();
    }

    @Operation(summary = "Obtener categoría por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categoría encontrada"),
            @ApiResponse(responseCode = "404", description = "Categoría no encontrada")
    })
    @GetMapping("/{id}")
    public ResponseEntity<Categoria> buscarPorId(
            @Parameter(
                    description = "Identificador de la categoría",
                    example = "1",
                    required = true)
            @PathVariable Long id) {

        return categoriaService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Crear categoría")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categoría creada"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Datos de entrada inválidos",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(
                    responseCode = "409",
                    description = "Ya existe una categoría con el mismo nombre",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    public Categoria crear(@Valid @RequestBody CategoriaRequest request) {
        return categoriaService.crear(request);
    }
}
