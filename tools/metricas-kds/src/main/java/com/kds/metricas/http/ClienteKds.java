package com.kds.metricas.http;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kds.metricas.http.Dtos.Orden;
import com.kds.metricas.http.Dtos.Pedido;
import com.kds.metricas.http.Dtos.PedidoEstado;
import com.kds.metricas.http.Dtos.Producto;
import com.kds.metricas.http.Dtos.RespuestaLogin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Cliente HTTP de la API del KDS. Todas las llamadas pasan por el API Gateway
 * (misma ruta que usan los frontends), nunca directamente contra un
 * microservicio, para que la medicion incluya el comportamiento real del
 * gateway.
 */
public class ClienteKds implements AutoCloseable {

    /** Respuesta cruda con el instante (nanoTime) en que se recibio. */
    public record RespuestaHttp(int status, String cuerpo, long nanoRecepcion) {
    }

    /** Valor deserializado junto al instante de recepcion. */
    public record ConTiempo<T>(T valor, long nanoRecepcion) {
    }

    private final String baseUrl;
    private final String email;
    private final String password;
    private final HttpClient http;
    private final ObjectMapper mapper;
    private String token;

    public ClienteKds(String baseUrl, String email, String password) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.email = email;
        this.password = password;
        this.mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    // ------------------------------------------------------------------
    // Sesion
    // ------------------------------------------------------------------

    /** Autentica contra POST /auth/login y guarda el token Bearer. */
    public synchronized String autenticar() {
        String cuerpo = json(Map.of("email", email, "password", password));
        RespuestaHttp respuesta = enviar("POST", "/auth/login", cuerpo, null);
        if (respuesta.status() != 200) {
            throw new ErrorKdsException(respuesta.status(),
                    "Fallo el login del usuario " + email + " (HTTP " + respuesta.status() + "): "
                            + recortar(respuesta.cuerpo()),
                    respuesta.cuerpo());
        }
        RespuestaLogin login = leer(respuesta.cuerpo(), RespuestaLogin.class);
        if (login.token() == null || login.token().isBlank()) {
            throw new ErrorKdsException(respuesta.status(), "La respuesta de /auth/login no incluye token.", respuesta.cuerpo());
        }
        this.token = login.token();
        return this.token;
    }

    public String token() {
        return token;
    }

    // ------------------------------------------------------------------
    // Endpoints
    // ------------------------------------------------------------------

    /** GET /productos */
    public List<Producto> listarProductos() {
        Producto[] arr = leer(lista("/productos"), Producto[].class);
        return arr == null ? List.of() : List.of(arr);
    }

    /** GET /productos con marca temporal de recepcion. */
    public ConTiempo<List<Producto>> listarProductosConTiempo() {
        RespuestaHttp r = ejecutar("GET", "/productos", null);
        Producto[] arr = leer(r.cuerpo(), Producto[].class);
        return new ConTiempo<>(arr == null ? List.of() : List.of(arr), r.nanoRecepcion());
    }

    /** GET /pedidos */
    public List<Pedido> listarPedidos() {
        Pedido[] arr = leer(lista("/pedidos"), Pedido[].class);
        return arr == null ? List.of() : List.of(arr);
    }

    /** POST /pedidos con un unico item {productoId, cantidad}. */
    public Pedido crearPedido(long productoId, int cantidad) {
        String cuerpo = json(Map.of(
                "items", List.of(Map.of("productoId", productoId, "cantidad", cantidad))));
        RespuestaHttp r = ejecutar("POST", "/pedidos", cuerpo);
        Pedido pedido = leer(r.cuerpo(), Pedido.class);
        if (pedido == null || pedido.id() == null) {
            throw new ErrorKdsException(r.status(), "La respuesta de POST /pedidos no incluye el id del pedido.", r.cuerpo());
        }
        return pedido;
    }

    /** GET /pedidos/{id}/estado */
    public PedidoEstado obtenerEstadoPedido(long pedidoId) {
        RespuestaHttp r = ejecutar("GET", "/pedidos/" + pedidoId + "/estado", null);
        return leer(r.cuerpo(), PedidoEstado.class);
    }

    /** GET /pedidos/{id}/estado con marca temporal de recepcion. */
    public ConTiempo<PedidoEstado> obtenerEstadoPedidoConTiempo(long pedidoId) {
        RespuestaHttp r = ejecutar("GET", "/pedidos/" + pedidoId + "/estado", null);
        return new ConTiempo<>(leer(r.cuerpo(), PedidoEstado.class), r.nanoRecepcion());
    }

    /** GET /ordenes */
    public List<Orden> listarOrdenes() {
        Orden[] arr = leer(lista("/ordenes"), Orden[].class);
        return arr == null ? List.of() : List.of(arr);
    }

    /** GET /ordenes con marca temporal de recepcion. */
    public ConTiempo<List<Orden>> listarOrdenesConTiempo() {
        RespuestaHttp r = ejecutar("GET", "/ordenes", null);
        Orden[] arr = leer(r.cuerpo(), Orden[].class);
        return new ConTiempo<>(arr == null ? List.of() : List.of(arr), r.nanoRecepcion());
    }

    /** PATCH /ordenes/{id}/estado */
    public Orden cambiarEstadoOrden(long ordenId, String estado) {
        RespuestaHttp r = ejecutar("PATCH", "/ordenes/" + ordenId + "/estado",
                json(Map.of("estado", estado)));
        Orden orden = leer(r.cuerpo(), Orden.class);
        if (orden == null) {
            throw new ErrorKdsException(r.status(), "Respuesta inesperada en PATCH /ordenes/" + ordenId + "/estado", r.cuerpo());
        }
        return orden;
    }

    /** Comprobacion de disponibilidad del API Gateway (sin autenticacion). */
    public boolean gatewayDisponible() {
        try {
            RespuestaHttp r = enviar("GET", "/actuator/health", null, null);
            return r.status() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Transporte
    // ------------------------------------------------------------------

    private String lista(String path) {
        RespuestaHttp r = ejecutar("GET", path, null);
        return r.cuerpo();
    }

    /** Envia una peticion autenticada; si el token expira, renueva la sesion y reintenta una vez. */
    private RespuestaHttp ejecutar(String metodo, String path, String cuerpo) {
        if (token == null || token.isBlank()) {
            autenticar();
        }
        RespuestaHttp r = enviar(metodo, path, cuerpo, token);
        if (r.status() == 401) {
            // Token expirado o invalido: se renueva la sesion y se reintenta una unica vez.
            autenticar();
            r = enviar(metodo, path, cuerpo, token);
        }
        if (r.status() < 200 || r.status() >= 300) {
            throw new ErrorKdsException(r.status(),
                    "HTTP " + r.status() + " en " + metodo + " " + path + ": " + recortar(r.cuerpo()),
                    r.cuerpo());
        }
        return r;
    }

    private RespuestaHttp enviar(String metodo, String path, String cuerpo, String bearer) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json");
        if (bearer != null && !bearer.isBlank()) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        if (cuerpo != null) {
            builder.header("Content-Type", "application/json");
            builder.method(metodo, HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8));
        } else {
            builder.method(metodo, HttpRequest.BodyPublishers.noBody());
        }
        try {
            HttpResponse<String> respuesta = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new RespuestaHttp(respuesta.statusCode(), respuesta.body(), System.nanoTime());
        } catch (IOException e) {
            throw new ErrorKdsException(-1, "Error de red en " + metodo + " " + path + ": " + e, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ErrorKdsException(-1, "Peticion interrumpida en " + metodo + " " + path, null);
        }
    }

    private <T> T leer(String json, Class<T> tipo) {
        try {
            return mapper.readValue(json, tipo);
        } catch (Exception e) {
            throw new ErrorKdsException(-1, "No se pudo interpretar la respuesta JSON (" + tipo.getSimpleName() + "): "
                    + recortar(json), json);
        }
    }

    private String json(Object valor) {
        try {
            return mapper.writeValueAsString(valor);
        } catch (Exception e) {
            throw new ErrorKdsException(-1, "No se pudo serializar la peticion JSON: " + e.getMessage(), null);
        }
    }

    private static String recortar(String texto) {
        if (texto == null) {
            return "";
        }
        String limpio = texto.replaceAll("\\s+", " ").trim();
        return limpio.length() > 400 ? limpio.substring(0, 400) + "..." : limpio;
    }

    /** Momento actual con precision de reloj monotono, para correlación con nanoTime. */
    public static Instant ahora() {
        return Instant.now();
    }

    @Override
    public void close() {
        // El HttpClient de Java 21 no tiene close(); se cierra el executor por defecto.
        http.executor().ifPresent(e -> {
            if (e instanceof java.util.concurrent.ExecutorService es) {
                es.shutdownNow();
            }
        });
    }
}
