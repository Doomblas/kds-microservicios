package com.kds.metricas;

import com.kds.metricas.config.Configuracion;
import com.kds.metricas.http.ClienteKds;
import com.kds.metricas.http.Dtos.Orden;
import com.kds.metricas.http.Dtos.Pedido;
import com.kds.metricas.http.Dtos.Producto;
import com.kds.metricas.medicion.Medidor;
import com.kds.metricas.modelo.Escenario;
import com.kds.metricas.modelo.Muestra;
import com.kds.metricas.modelo.ResultadoMedicion;
import com.kds.metricas.salida.Consola;
import com.kds.metricas.salida.ExportadorCsv;
import com.kds.metricas.salida.ExportadorJson;
import com.kds.metricas.salida.GeneradorGraficas;
import com.kds.metricas.salida.GeneradorInformes;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Herramienta de medicion automatica de la propagacion de eventos del KDS.
 *
 * <p>No es un microservicio: es una aplicacion de linea de comandos que se
 * conecta al stack KDS ya desplegado a traves del API Gateway, ejecuta las
 * pruebas de propagacion y genera los artefactos del informe.</p>
 *
 * <p>Ejecucion:</p>
 * <pre>
 * KDS_ADMIN_EMAIL=... KDS_ADMIN_PASSWORD=... KDS_NUM_PRUEBAS=20 ./mvnw exec:java
 * </pre>
 */
public final class MetricasApplication {

    private MetricasApplication() {
    }

    public static void main(String[] args) {
        // Formato numerico uniforme en toda la salida (CSV, JSON, graficas y consola).
        Locale.setDefault(Locale.US);
        Consola consola = new Consola(System.out);
        int codigo = ejecutar(consola);
        System.exit(codigo);
    }

    /** Ejecuta la herramienta completa. Devuelve el codigo de salida del proceso. */
    public static int ejecutar(Consola consola) {
        Configuracion cfg;
        try {
            cfg = Configuracion.desdeEntorno();
        } catch (RuntimeException e) {
            consola.error(e.getMessage());
            return 2;
        }

        consola.cabecera("MEDICION DE PROPAGACION DE EVENTOS KDS");
        consola.linea("Fecha de ejecucion   : " + Instant.now());
        consola.linea("API Gateway          : " + cfg.baseUrl());
        consola.linea("Pruebas por escenario : " + cfg.numPruebas());
        consola.linea("Timeout por evento   : " + cfg.timeoutMs() + " ms");
        consola.linea("Intervalo de sondeo  : " + cfg.pollIntervalMs() + " ms");
        consola.vacio();

        Path directorio = cfg.prepararDirectorioResultados();

        try (ClienteKds cliente = new ClienteKds(cfg.baseUrl(), cfg.email(), cfg.password())) {
            long productoId;
            try {
                productoId = validarPrecondiciones(consola, cliente, cfg);
            } catch (RuntimeException e) {
                consola.error("No se puede iniciar la medicion: " + e.getMessage());
                return 3;
            }

            consola.vacio();
            consola.cabecera("EJECUCION DE LAS PRUEBAS");
            long inicio = System.nanoTime();

            Medidor medidor = new Medidor(cliente, cfg, productoId);
            List<Muestra> muestras = medidor.ejecutar();

            double duracionSegundos = (System.nanoTime() - inicio) / 1_000_000_000.0;
            consola.vacio();
            consola.linea("Duracion total de la campana: " + String.format(Locale.ROOT, "%.2f", duracionSegundos) + " s");
            consola.vacio();

            ResultadoMedicion resultado = ResultadoMedicion.de(muestras, cfg.baseUrl(), cfg.numPruebas(),
                    cfg.timeoutMs(), cfg.pollIntervalMs());

            imprimirResultados(consola, resultado);

            List<Path> generados = new ArrayList<>();
            try {
                generados.add(ExportadorCsv.escribirDetalle(directorio, muestras));
                generados.add(ExportadorCsv.escribirResumen(directorio, resultado));
                generados.add(ExportadorJson.escribirResumen(directorio, resultado));
                generados.add(GeneradorGraficas.graficaPromedio(directorio, resultado));
                generados.add(GeneradorGraficas.graficaMinMediaMax(directorio, resultado));
                generados.add(GeneradorInformes.escribirTabla(directorio, resultado));
                generados.add(GeneradorInformes.escribirResumen(directorio, resultado));
                generados.add(GeneradorInformes.escribirMetodologia(directorio, resultado));
            } catch (Exception e) {
                consola.error("Error al generar los ficheros de resultados: " + e);
                return 4;
            }

            consola.cabecera("ARCHIVOS GENERADOS");
            for (Path p : generados) {
                consola.linea("  " + p.toAbsolutePath());
            }
            consola.vacio();
            return 0;
        } catch (Exception e) {
            consola.error("Fallo inesperado durante la medicion: " + e);
            e.printStackTrace(System.out);
            return 5;
        }
    }

    // ------------------------------------------------------------------
    // Validaciones previas
    // ------------------------------------------------------------------

    /**
     * Comprueba que el sistema esta operativo antes de medir. Devuelve el id
     * del producto que se usara en las pruebas.
     */
    private static long validarPrecondiciones(Consola consola, ClienteKds cliente, Configuracion cfg) {
        consola.cabecera("VALIDACIONES PREVIAS");

        // 1. API Gateway disponible.
        if (!cliente.gatewayDisponible()) {
            throw new IllegalStateException("El API Gateway no responde en " + cfg.baseUrl()
                    + " (GET /actuator/health). Verifique que el stack este levantado.");
        }
        consola.ok("1. API Gateway disponible en " + cfg.baseUrl());

        // 2. Autenticacion.
        String token = cliente.autenticar();
        consola.ok("2. Autenticacion correcta mediante POST /auth/login (token de " + token.length() + " caracteres)");

        // 3. Catalogo con al menos un producto disponible.
        List<Producto> productos = cliente.listarProductos();
        long productoId = Medidor.seleccionarProducto(productos, cfg.productoId());
        consola.ok("3. Catalogo de ms-menu disponible: producto de prueba id=" + productoId
                + " (" + productos.size() + " productos en total)");

        // 4. ms-pedidos responde.
        try {
            cliente.listarPedidos();
        } catch (Exception e) {
            throw new IllegalStateException("ms-pedidos no responde a traves del API Gateway: " + e.getMessage(), e);
        }
        consola.ok("4. ms-pedidos responde (GET /pedidos)");

        // 5. ms-display-cocina responde.
        try {
            cliente.listarOrdenes();
        } catch (Exception e) {
            throw new IllegalStateException("ms-display-cocina no responde a traves del API Gateway: " + e.getMessage(), e);
        }
        consola.ok("5. ms-display-cocina responde (GET /ordenes)");

        // 6. Flujo de eventos RabbitMQ operativo en los dos sentidos.
        if (cfg.ejecutarHumo()) {
            probarFlujoEventos(consola, cliente, cfg, productoId);
        } else {
            consola.aviso("6. Prueba de humo del flujo de eventos OMITIDA (KDS_EJECUTAR_HUMO=false)");
        }
        return productoId;
    }

    /**
     * Prueba de humo: crea un pedido y comprueba que viaja por RabbitMQ en los
     * dos sentidos (Pedidos -> Cocina y Cocina -> Pedidos). Si falla, el broker
     * o alguno de los consumidores no esta operativo y no tiene sentido medir.
     */
    private static void probarFlujoEventos(Consola consola, ClienteKds cliente, Configuracion cfg, long productoId) {
        Pedido pedido = cliente.crearPedido(productoId, cfg.cantidad());
        Long ordenId = null;

        long limite = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(cfg.timeoutMs());
        while (System.nanoTime() < limite) {
            for (Orden orden : cliente.listarOrdenes()) {
                if (orden.pedidoId() != null && orden.pedidoId().equals(pedido.id())) {
                    ordenId = orden.id();
                    break;
                }
            }
            if (ordenId != null) {
                break;
            }
            dormir(20);
        }
        if (ordenId == null) {
            throw new IllegalStateException("El flujo de eventos no responde: el pedido " + pedido.id()
                    + " (" + pedido.numeroPedido() + ") no llego a ms-display-cocina. "
                    + "Revise RabbitMQ y el listener pedido.creado.");
        }

        cliente.cambiarEstadoOrden(ordenId, "PREPARANDO");
        long limite2 = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(cfg.timeoutMs());
        while (System.nanoTime() < limite2) {
            String estado = cliente.obtenerEstadoPedido(pedido.id()).estado();
            if ("PREPARANDO".equals(estado)) {
                cliente.cambiarEstadoOrden(ordenId, "LISTO");
                consola.ok("6. Flujo de eventos RabbitMQ operativo en los dos sentidos "
                        + "(pedido.creado y pedido.estado.actualizado). Pedido de comprobacion: "
                        + pedido.id() + " / " + pedido.numeroPedido());
                return;
            }
            dormir(20);
        }
        throw new IllegalStateException("El flujo de eventos no responde en sentido Cocina -> Pedidos: "
                + "el pedido " + pedido.id() + " no reflejaba el estado PREPARANDO.");
    }

    // ------------------------------------------------------------------
    // Presentacion
    // ------------------------------------------------------------------

    private static void imprimirResultados(Consola consola, ResultadoMedicion resultado) {
        consola.cabecera("RESULTADOS DE PROPAGACION KDS");
        for (Escenario escenario : ResultadoMedicion.ORDEN_ESCENARIOS) {
            consola.bloqueEscenario(escenario, resultado.de(escenario));
        }
        consola.detalleFallos(resultado);
        consola.tablaResumen(resultado);
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ejecucion interrumpida", e);
        }
    }

    /** Tiempo total de la campana, en segundos. */
    static double segundos(long nanos) {
        return Duration.ofNanos(nanos).toMillis() / 1000.0;
    }
}
