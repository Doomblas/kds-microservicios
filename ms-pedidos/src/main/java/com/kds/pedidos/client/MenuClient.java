package com.kds.pedidos.client;

import com.kds.pedidos.dto.ProductoMenuResponse;
import com.kds.pedidos.exception.RecursoNoEncontradoException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class MenuClient {

    private final RestClient restClient;

    public MenuClient(@Value("${kds.menu.base-url:http://localhost:8080}") String menuBaseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(menuBaseUrl)
                .build();
    }

    public ProductoMenuResponse obtenerProducto(Long productoId) {
        ProductoMenuResponse producto = restClient.get()
                .uri("/productos/{id}", productoId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                    throw new RecursoNoEncontradoException(
                            "Producto no encontrado en ms-menu con id " + productoId);
                })
                .body(ProductoMenuResponse.class);

        if (producto == null) {
            throw new RecursoNoEncontradoException(
                    "Producto no encontrado en ms-menu con id " + productoId);
        }

        if (Boolean.FALSE.equals(producto.disponible())) {
            throw new RecursoNoEncontradoException(
                    "Producto no disponible en ms-menu con id " + productoId);
        }

        return producto;
    }
}
