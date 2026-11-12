package com.kds.menu.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.Map;

@Schema(description = "Respuesta estandar de error de la API. Puede representar errores HTTP 400, 404, 409 y 500.")
public class ApiError {

    @Schema(
            description = "Fecha y hora en que ocurrio el error",
            example = "2026-11-10T18:45:12.123",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime timestamp;

    @Schema(
            description = "Codigo HTTP del error",
            example = "404",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private int status;

    @Schema(
            description = "Nombre estandar del estado HTTP",
            example = "Not Found",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String error;

    @Schema(
            description = "Mensaje descriptivo del error",
            example = "Producto no encontrado con id: 99",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String message;

    @Schema(
            description = "Ruta de la solicitud que genero el error",
            example = "/productos/99",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String path;

    @Schema(
            description = "Errores de validacion por campo. Se incluye principalmente en errores HTTP 400.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, String> fieldErrors;

    public ApiError() {
    }

    public ApiError(int status, String error, String message, String path) {
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
    }

    public ApiError(
            int status,
            String error,
            String message,
            String path,
            Map<String, String> fieldErrors) {

        this(status, error, message, path);
        this.fieldErrors = fieldErrors;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public int getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    public String getPath() {
        return path;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
