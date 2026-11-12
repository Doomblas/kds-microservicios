package com.kds.metricas.medicion;

import com.kds.metricas.config.Configuracion;
import com.kds.metricas.http.ClienteKds;
import com.kds.metricas.modelo.Escenario;
import com.kds.metricas.modelo.Estadisticas;
import com.kds.metricas.modelo.Muestra;
import com.kds.metricas.modelo.ResultadoMedicion;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba de la ruta de fallo de la medicion: se levanta un servidor HTTP
 * mínimo que acepta el pedido pero nunca publica la orden en cocina, de modo
 * que la propagacion no se detecta dentro del tiempo de espera.
 */
class MedidorTest {

    private HttpServer servidor;
    private String baseUrl;

    @BeforeEach
    void levantarServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/auth/login", e -> responder(e, 200,
                "{\"token\":\"token-de-prueba\",\"tipo\":\"Bearer\",\"expiraEnSegundos\":1800}"));
        servidor.createContext("/pedidos", e -> {
            if ("POST".equals(e.getRequestMethod())) {
                responder(e, 201, "{\"id\":1,\"numeroPedido\":\"ORD-1\",\"estado\":\"PENDIENTE\",\"total\":5.00}");
            } else {
                responder(e, 200, "[]");
            }
        });
        servidor.createContext("/pedidos/1/estado", e ->
                responder(e, 200, "{\"id\":1,\"numeroPedido\":\"ORD-1\",\"estado\":\"PENDIENTE\"}"));
        servidor.createContext("/ordenes", e -> responder(e, 200, "[]"));
        servidor.createContext("/ordenes/1/estado", e ->
                responder(e, 200, "{\"id\":1,\"pedidoId\":1,\"numeroPedido\":\"ORD-1\",\"estado\":\"PREPARANDO\"}"));
        servidor.start();
        baseUrl = "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    @AfterEach
    void detenerServidor() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    private static void responder(HttpExchange e, int status, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        e.getResponseHeaders().add("Content-Type", "application/json");
        e.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = e.getResponseBody()) {
            os.write(bytes);
        }
    }

    @Test
    @DisplayName("Si la propagacion no llega, la muestra se registra como fallida sin latencia inventada")
    void timeoutRegistradoComoFallo() {
        Configuracion cfg = new Configuracion(baseUrl, "admin@kds.local", "clave", 2, 250, 10, 0, 1L, 1,
                Path.of("resultados"), false);

        try (ClienteKds cliente = new ClienteKds(cfg.baseUrl(), cfg.email(), cfg.password())) {
            cliente.autenticar();
            List<Muestra> muestras = new Medidor(cliente, cfg, 1L).ejecutar();

            // Se generan tres muestras por iteracion: una por escenario.
            assertEquals(6, muestras.size());

            Muestra nuevo = muestras.stream()
                    .filter(m -> m.escenario() == Escenario.PEDIDO_NUEVO)
                    .findFirst().orElseThrow();
            assertFalse(nuevo.exito());
            assertNull(nuevo.latenciaMs());
            assertTrue(nuevo.detalle().startsWith("TIMEOUT"), nuevo.detalle());
            assertEquals(1L, nuevo.pedidoId());

            // Los escenarios dependientes quedan registrados como omitidos.
            Muestra preparando = muestras.stream()
                    .filter(m -> m.escenario() == Escenario.CAMBIO_PREPARANDO)
                    .findFirst().orElseThrow();
            assertFalse(preparando.exito());
            assertTrue(preparando.detalle().startsWith("OMITIDO"), preparando.detalle());

            // Las estadisticas no inventan latencias para las pruebas fallidas.
            ResultadoMedicion resultado = ResultadoMedicion.de(muestras, cfg.baseUrl(), cfg.numPruebas(),
                    cfg.timeoutMs(), cfg.pollIntervalMs());
            Estadisticas e = resultado.de(Escenario.PEDIDO_NUEVO);
            assertEquals(2, e.pruebas());
            assertEquals(0, e.exitos());
            assertEquals(2, e.fallos());
            assertEquals(0.0, e.exitoPct(), 1e-9);
            assertNull(e.media());
            assertNull(e.mediana());
        }
    }
}
