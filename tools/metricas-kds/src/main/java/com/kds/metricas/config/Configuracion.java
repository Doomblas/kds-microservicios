package com.kds.metricas.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuracion de la herramienta de metricas, alimentada exclusivamente por
 * variables de entorno. No se almacena ninguna credencial en el repositorio.
 *
 * <pre>
 * KDS_BASE_URL         URL base del API Gateway (por defecto http://localhost:8080)
 * KDS_ADMIN_EMAIL      correo del usuario ADMIN de prueba
 * KDS_ADMIN_PASSWORD   contrasena del usuario ADMIN de prueba
 * KDS_NUM_PRUEBAS      numero de iteraciones completas (por defecto 20)
 * KDS_TIMEOUT_MS       timeout de espera por evento (por defecto 10000)
 * KDS_POLL_INTERVAL_MS intervalo de sondeo (por defecto 10)
 * KDS_CALENTAMIENTO    iteraciones previas no contabilizadas (por defecto 0)
 * KDS_PRODUCTO_ID      producto a usar; si no se indica se toma el primero disponible
 * KDS_CANTIDAD         cantidad de unidades por producto (por defecto 1)
 * KDS_DIR_RESULTADOS   directorio de salida (por defecto ./resultados)
 * KDS_EJECUTAR_HUMO    true|false, prueba de humo previa del flujo de eventos (por defecto true)
 * </pre>
 */
public record Configuracion(
        String baseUrl,
        String email,
        String password,
        int numPruebas,
        long timeoutMs,
        long pollIntervalMs,
        int calentamiento,
        Long productoId,
        int cantidad,
        Path directorioResultados,
        boolean ejecutarHumo
) {

    public static final String BASE_URL_POR_DEFECTO = "http://localhost:8080";

    public Configuracion {
        baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (numPruebas <= 0) {
            throw new IllegalArgumentException("KDS_NUM_PRUEBAS debe ser un entero mayor que cero.");
        }
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("KDS_TIMEOUT_MS debe ser un entero mayor que cero.");
        }
        if (pollIntervalMs <= 0) {
            throw new IllegalArgumentException("KDS_POLL_INTERVAL_MS debe ser un entero mayor que cero.");
        }
        if (cantidad <= 0) {
            throw new IllegalArgumentException("KDS_CANTIDAD debe ser un entero mayor que cero.");
        }
        if (calentamiento < 0) {
            throw new IllegalArgumentException("KDS_CALENTAMIENTO no puede ser negativo.");
        }
    }

    public static Configuracion desdeEntorno() {
        String baseUrl = env("KDS_BASE_URL", BASE_URL_POR_DEFECTO);
        String email = env("KDS_ADMIN_EMAIL", "");
        String password = env("KDS_ADMIN_PASSWORD", "");

        if (email.isBlank() || password.isBlank()) {
            throw new IllegalStateException(
                    "Faltan credenciales. Define KDS_ADMIN_EMAIL y KDS_ADMIN_PASSWORD "
                            + "con un usuario ADMIN valido del sistema KDS (no se guardan en el repositorio).");
        }

        return new Configuracion(
                baseUrl,
                email.trim(),
                password,
                (int) entero("KDS_NUM_PRUEBAS", 20),
                entero("KDS_TIMEOUT_MS", 10_000),
                entero("KDS_POLL_INTERVAL_MS", 10),
                (int) entero("KDS_CALENTAMIENTO", 0),
                enteroOpcional("KDS_PRODUCTO_ID"),
                (int) entero("KDS_CANTIDAD", 1),
                Paths.get(env("KDS_DIR_RESULTADOS", "resultados")),
                booleano("KDS_EJECUTAR_HUMO", true));
    }

    /** Crea el directorio de resultados si hace falta. */
    public Path prepararDirectorioResultados() {
        try {
            Files.createDirectories(directorioResultados);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "No se pudo crear el directorio de resultados " + directorioResultados + ": " + e.getMessage(), e);
        }
        return directorioResultados;
    }

    private static String env(String nombre, String porDefecto) {
        String valor = System.getenv(nombre);
        return valor == null || valor.isBlank() ? porDefecto : valor.trim();
    }

    private static long entero(String nombre, long porDefecto) {
        String valor = env(nombre, String.valueOf(porDefecto));
        try {
            return Long.parseLong(valor);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(nombre + " debe ser un numero entero (valor actual: " + valor + ")");
        }
    }

    private static Long enteroOpcional(String nombre) {
        String valor = env(nombre, "");
        if (valor.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(valor);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(nombre + " debe ser un numero entero (valor actual: " + valor + ")");
        }
    }

    private static boolean booleano(String nombre, boolean porDefecto) {
        String valor = env(nombre, String.valueOf(porDefecto));
        return Boolean.parseBoolean(valor);
    }
}
