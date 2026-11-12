package com.kds.metricas.salida;

import com.kds.metricas.modelo.Estadisticas;
import com.kds.metricas.modelo.Escenario;
import com.kds.metricas.modelo.Muestra;
import com.kds.metricas.modelo.ResultadoMedicion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportadorCsvTest {

    @TempDir
    Path temporal;

    @Test
    @DisplayName("CSV de detalle: cabecera y una fila por muestra")
    void csvDetalle() throws Exception {
        List<Muestra> muestras = List.of(
                Muestra.exitosa(Escenario.PEDIDO_NUEVO, 1, 10L, "ORD-1", 7L, 12.3456, Instant.now()),
                Muestra.fallida(Escenario.CAMBIO_LISTO, 1, "TIMEOUT: no se reflejo LISTO", Instant.now()));

        Path destino = ExportadorCsv.escribirDetalle(temporal, muestras);
        List<String> lineas = Files.readAllLines(destino, StandardCharsets.UTF_8);

        assertEquals("latencias_detalle.csv", destino.getFileName().toString());
        assertEquals(3, lineas.size());
        assertEquals("escenario,prueba,pedido_id,numero_pedido,exito,latencia_ms,timestamp,detalle", lineas.get(0));
        assertTrue(lineas.get(1).startsWith("Pedido nuevo,1,10,ORD-1,true,12.346,"));
        assertTrue(lineas.get(2).startsWith("Cambio a LISTO,1,,,false,,"));
        assertTrue(lineas.get(2).contains("TIMEOUT: no se reflejo LISTO"));
    }

    @Test
    @DisplayName("CSV de resumen: una fila por escenario con las metricas")
    void csvResumen() throws Exception {
        List<Muestra> muestras = List.of(
                Muestra.exitosa(Escenario.PEDIDO_NUEVO, 1, 10L, "ORD-1", 7L, 10.0, Instant.now()),
                Muestra.exitosa(Escenario.PEDIDO_NUEVO, 2, 11L, "ORD-2", 8L, 20.0, Instant.now()),
                Muestra.exitosa(Escenario.CAMBIO_PREPARANDO, 1, 10L, "ORD-1", 7L, 30.0, Instant.now()),
                Muestra.exitosa(Escenario.CAMBIO_LISTO, 1, 10L, "ORD-1", 7L, 40.0, Instant.now()));

        ResultadoMedicion resultado = ResultadoMedicion.de(muestras, "http://localhost:8080", 2, 10000, 10);
        Path destino = ExportadorCsv.escribirResumen(temporal, resultado);
        List<String> lineas = Files.readAllLines(destino, StandardCharsets.UTF_8);

        assertEquals("latencias_resumen.csv", destino.getFileName().toString());
        assertEquals(4, lineas.size());
        assertEquals("escenario,pruebas,exitos,fallos,exito_pct,latencia_media_ms,min_ms,max_ms,mediana_ms,desviacion_ms",
                lineas.get(0));
        assertEquals("Pedido nuevo,2,2,0,100.00,15.000,10.000,20.000,15.000,7.071", lineas.get(1));
        assertEquals("Cambio a PREPARANDO,1,1,0,100.00,30.000,30.000,30.000,30.000,0.000", lineas.get(2));
        assertEquals("Cambio a LISTO,1,1,0,100.00,40.000,40.000,40.000,40.000,0.000", lineas.get(3));
    }

    @Test
    @DisplayName("CSV de resumen sin exitos deja las metricas de latencia vacias")
    void csvResumenSinExitos() throws Exception {
        List<Muestra> muestras = List.of(
                Muestra.fallida(Escenario.PEDIDO_NUEVO, 1, "ERROR", Instant.now()));

        ResultadoMedicion resultado = ResultadoMedicion.de(muestras, "http://localhost:8080", 1, 10000, 10);
        Path destino = ExportadorCsv.escribirResumen(temporal, resultado);
        List<String> lineas = Files.readAllLines(destino, StandardCharsets.UTF_8);

        assertEquals("Pedido nuevo,1,0,1,0.00,,,,,", lineas.get(1));
    }

    @Test
    @DisplayName("Escapado de valores que contienen comas o comillas")
    void escapado() {
        assertEquals("simple", ExportadorCsv.csv("simple"));
        assertEquals("\"con,coma\"", ExportadorCsv.csv("con,coma"));
        assertEquals("\"con\"\"comillas\"\"\"", ExportadorCsv.csv("con\"comillas\""));
        assertEquals("", ExportadorCsv.csv(null));
    }

    @Test
    @DisplayName("JSON de resumen contiene las metricas de cada escenario")
    void jsonResumen() throws Exception {
        List<Muestra> muestras = List.of(
                Muestra.exitosa(Escenario.PEDIDO_NUEVO, 1, 10L, "ORD-1", 7L, 12.5, Instant.now()),
                Muestra.exitosa(Escenario.CAMBIO_PREPARANDO, 1, 10L, "ORD-1", 7L, 22.5, Instant.now()),
                Muestra.exitosa(Escenario.CAMBIO_LISTO, 1, 10L, "ORD-1", 7L, 32.5, Instant.now()));

        ResultadoMedicion resultado = ResultadoMedicion.de(muestras, "http://localhost:8080", 1, 10000, 10);
        Path destino = ExportadorJson.escribirResumen(temporal, resultado);
        String json = Files.readString(destino, StandardCharsets.UTF_8);

        assertTrue(json.contains("\"herramienta\" : \"metricas-kds\""));
        assertTrue(json.contains("\"escenario\" : \"Pedido nuevo\""));
        assertTrue(json.contains("\"latenciaMediaMs\" : 12.5"));
        assertTrue(json.contains("\"exitoPct\" : 100.0"));
        assertTrue(json.contains("\"numPruebasPorEscenario\" : 1"));
    }

    @Test
    @DisplayName("Estadisticas de una corrida completa")
    void estadisticasDeCorrida() {
        List<Muestra> muestras = List.of(
                Muestra.exitosa(Escenario.PEDIDO_NUEVO, 1, 1L, "ORD-1", 1L, 100.0, Instant.now()),
                Muestra.exitosa(Escenario.PEDIDO_NUEVO, 2, 2L, "ORD-2", 2L, 200.0, Instant.now()));

        ResultadoMedicion resultado = ResultadoMedicion.de(muestras, "http://localhost:8080", 2, 10000, 10);
        Estadisticas e = resultado.de(Escenario.PEDIDO_NUEVO);

        assertEquals(2, e.pruebas());
        assertEquals(150.0, e.media(), 1e-9);
        // Desviacion estandar muestral de {100, 200}: sqrt(5000/1) = 70.7107
        assertEquals(70.710678, e.desviacion(), 1e-5);
    }
}
