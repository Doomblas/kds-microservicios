package com.kds.cocina.exception;

import com.kds.cocina.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> manejarNoEncontrado(
            RecursoNoEncontradoException ex, HttpServletRequest request) {
        return construir(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(TransicionEstadoInvalidaException.class)
    public ResponseEntity<ErrorResponse> manejarTransicionInvalida(
            TransicionEstadoInvalidaException ex, HttpServletRequest request) {
        return construir(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> manejarValidacion(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String mensaje = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Solicitud invalida");
        return construir(HttpStatus.BAD_REQUEST, mensaje, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> manejarCuerpoInvalido(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return construir(HttpStatus.BAD_REQUEST,
                "Cuerpo de la solicitud invalido o estado no permitido", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> manejarArgumentoInvalido(
            IllegalArgumentException ex, HttpServletRequest request) {
        return construir(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> manejarErrorInesperado(
            Exception ex, HttpServletRequest request) {
        log.error("Error no controlado procesando {}", request.getRequestURI(), ex);
        return construir(HttpStatus.INTERNAL_SERVER_ERROR,
                "Error interno del servidor de cocina", request);
    }

    private ResponseEntity<ErrorResponse> construir(
            HttpStatus status, String mensaje, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                mensaje,
                request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
