package com.kds.pedidos.client;

import com.kds.pedidos.dto.ProductoMenuResponse;
import com.kds.pedidos.exception.RecursoNoEncontradoException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class MenuClient {

    private final RestClient restClient;

    public MenuClient(@Value("${kds.menu.base-url:http://localhost:8080}") String menuBaseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(menuBaseUrl)
                .build();
    }

    public ProductoMenuResponse obtenerProducto(Long productoId) {
        RestClient.RequestHeadersSpec<?> solicitud = restClient.get()
                .uri("/productos/{id}", productoId);

        String authorization = authorizationActual();
        if (authorization != null) {
            solicitud = solicitud.header(HttpHeaders.AUTHORIZATION, authorization);
        }

        ProductoMenuResponse producto = solicitud
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

    /**
     * Propaga el Bearer del usuario de caja para que ms-menu aplique su propia
     * autorización. La llamada interna no se salta la seguridad del catálogo.
     */
    private String authorizationActual() {
        if (RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
        }
        return null;
    }
}
