package com.kds.metricas.http;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTOs minimos de la API del KDS. Solo se mapean los campos que la
 * herramienta necesita; el resto se ignora para no acoplar la herramienta a
 * cambios de contrato que no afecten a la medicion.
 */
public final class Dtos {

    private Dtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RespuestaLogin(String token, String tipo, Long expiraEnSegundos, Usuario usuario) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usuario(Long id, String nombre, String email, String rol, String estado, String fechaCreacion) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Producto(Long id, String nombre, java.math.BigDecimal precio, Boolean disponible) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Pedido(Long id, String numeroPedido, String estado, java.math.BigDecimal total, String fechaCreacion) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PedidoEstado(Long id, String numeroPedido, String estado) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Orden(Long id, Long pedidoId, String numeroPedido, String estado,
                        String fechaRecepcion, String fechaInicio, String fechaFinalizacion,
                        java.util.List<ItemOrden> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ItemOrden(Long productoId, String nombreProducto, Integer cantidad) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErrorApi(String timestamp, Integer status, String error, String message, String path) {
    }
}
