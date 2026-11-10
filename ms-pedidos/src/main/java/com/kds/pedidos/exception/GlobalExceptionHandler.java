package com.kds.pedidos.exception;

import com.kds.pedidos.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> manejarNoEncontrado(RecursoNoEncontradoException ex, HttpServletRequest r) {
        return construir(HttpStatus.NOT_FOUND, ex.getMessage(), r);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> manejarValidacion(MethodArgumentNotValidException ex, HttpServletRequest r) {
        String mensaje = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Estructura de payload inválida");
        return construir(HttpStatus.BAD_REQUEST, mensaje, r);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> manejarIntegridad(DataIntegrityViolationException ex, HttpServletRequest r) {
        log.error("Violación de integridad en {}: {}", r.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return construir(HttpStatus.CONFLICT, "Conflicto de datos: posible numeroPedido duplicado", r);
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ErrorResponse> manejarMenuCaido(ResourceAccessException ex, HttpServletRequest r) {
        log.error("ms-menu no accesible desde {}: {}", r.getRequestURI(), ex.getMessage());
        return construir(HttpStatus.SERVICE_UNAVAILABLE, "ms-menu no está disponible en este momento", r);
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ErrorResponse> manejarErrorMenu(RestClientException ex, HttpServletRequest r) {
        log.error("Error consultando ms-menu desde {}: {}", r.getRequestURI(), ex.getMessage());
        return construir(HttpStatus.BAD_GATEWAY, "Error al consultar ms-menu", r);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> manejarErrorInesperado(Exception ex, HttpServletRequest r) {
        log.error("Error inesperado en {}: {}", r.getRequestURI(), ex.getMessage(), ex);
        return construir(HttpStatus.INTERNAL_SERVER_ERROR, "Fallo interno en el subsistema de facturación y pedidos", r);
    }

    private ResponseEntity<ErrorResponse> construir(HttpStatus status, String msg, HttpServletRequest r) {
        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(), status.value(), status.getReasonPhrase(), msg, r.getRequestURI());
        return ResponseEntity.status(status).body(error);
    }
}
