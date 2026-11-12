package com.kds.menu.controller;

import com.kds.menu.dto.ApiError;
import com.kds.menu.dto.DisponibilidadRequest;
import com.kds.menu.dto.ProductoRequest;
import com.kds.menu.model.Producto;
import com.kds.menu.service.ProductoService;
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

@Tag(name = "Productos", description = "Administración del catálogo de productos")
@RestController
@RequestMapping("/productos")
public class ProductoController {

    private final ProductoService productoService;

    public ProductoController(ProductoService productoService) {
        this.productoService = productoService;
    }

    @Operation(
            summary = "Listar productos",
            description = "Obtiene todos los productos registrados en el menú.")
    @ApiResponse(responseCode = "200", description = "Listado de productos")
    @GetMapping
    public List<Producto> listarTodos() {
        return productoService.listarTodos();
    }

    @Operation(summary = "Obtener producto por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Producto encontrado"),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado")
    })
    @GetMapping("/{id}")
    public ResponseEntity<Producto> buscarPorId(
            @Parameter(
                    description = "Identificador del producto",
                    example = "1",
                    required = true)
            @PathVariable Long id) {

        return productoService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Crear producto")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Producto creado"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Datos de entrada inválidos",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Categoría no encontrada",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    public Producto crear(@Valid @RequestBody ProductoRequest request) {
        return productoService.crear(request);
    }

    @Operation(summary = "Actualizar producto")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Producto actualizado"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Datos de entrada inválidos",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Producto o categoría no encontrados",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class)))
    })
    @PutMapping("/{id}")
    public Producto actualizar(
            @Parameter(
                    description = "Identificador del producto",
                    example = "1",
                    required = true)
            @PathVariable Long id,
            @Valid @RequestBody ProductoRequest request) {

        return productoService.actualizar(id, request);
    }

    @Operation(
            summary = "Actualizar disponibilidad de producto",
            description = "Modifica únicamente el estado de disponibilidad del producto.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Disponibilidad actualizada"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Datos de entrada inválidos",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Producto no encontrado",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class)))
    })
    @PatchMapping("/{id}/disponibilidad")
    public Producto actualizarDisponibilidad(
            @Parameter(
                    description = "Identificador del producto",
                    example = "1",
                    required = true)
            @PathVariable Long id,
            @Valid @RequestBody DisponibilidadRequest request) {

        return productoService.actualizarDisponibilidad(id, request.getDisponible());
    }
}
