package com.kds.pedidos.controller;

import com.kds.pedidos.dto.CrearPedidoRequest;
import com.kds.pedidos.dto.PedidoEstadoResponse;
import com.kds.pedidos.dto.PedidoResponse;
import com.kds.pedidos.service.PedidoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/pedidos")
@Tag(name = "Pedidos y Caja", description = "Gestión de ventas y control de estados de ordenes de despacho")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar una nueva orden")
    public PedidoResponse crearPedido(@Valid @RequestBody CrearPedidoRequest request) {
        return pedidoService.registrarPedido(request);
    }

    @GetMapping
    @Operation(summary = "Listar el histórico de pedidos de caja")
    public List<PedidoResponse> listar() {
        return pedidoService.listarPedidos();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar el detalle global de un pedido")
    public PedidoResponse obtener(@PathVariable Long id) {
        return pedidoService.obtenerPedido(id);
    }

    @GetMapping("/{id}/estado")
    @Operation(summary = "Consultar estado específico del pedido")
    public PedidoEstadoResponse obtenerEstado(@PathVariable Long id) {
        return pedidoService.obtenerEstadoPedido(id);
    }
}
