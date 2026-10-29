package com.kds.pedidos.exception;

import com.kds.pedidos.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> manejarErrorInesperado(Exception ex, HttpServletRequest r) {
        return construir(HttpStatus.INTERNAL_SERVER_ERROR, "Fallo interno en el subsistema de facturación y pedidos", r);
    }

    private ResponseEntity<ErrorResponse> construir(HttpStatus status, String msg, HttpServletRequest r) {
        ErrorResponse error = new ErrorResponse(LocalDateTime.now(), status.value(), status.getReasonPhrase(), msg, r.getRequestURI());
        return ResponseEntity.status(status).body(error);
    }
}
