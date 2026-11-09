package com.kds.cocina.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.kds.cocina.dto.ErrorResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unaRutaInexistenteDevuelve404YNo500() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        NoResourceFoundException exception = new NoResourceFoundException(
                HttpMethod.GET, "/", "No static resource");

        ResponseEntity<ErrorResponse> response =
                handler.manejarRutaNoEncontrada(exception, request);

        assertEquals(404, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().status());
        assertEquals("Recurso no encontrado", response.getBody().message());
        assertEquals("/", response.getBody().path());
    }
}
