package com.kds.metricas.salida;

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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneradorInformesTest {

    @TempDir
    Path temporal;

    private static ResultadoMedicion resultadoDeEjemplo(int exitos, int fallos) {
        List<Muestra> muestras = new java.util.ArrayList<>();
        for (int i = 1; i <= exitos; i++) {
            muestras.add(Muestra.exitosa(Escenario.PEDIDO_NUEVO, i, (long) i, "ORD-" + i, (long) i,
                    100.0 + i, Instant.now()));
            muestras.add(Muestra.exitosa(Escenario.CAMBIO_PREPARANDO, i, (long) i, "ORD-" + i, (long) i,
                    50.0 + i, Instant.now()));
            muestras.add(Muestra.exitosa(Escenario.CAMBIO_LISTO, i, (long) i, "ORD-" + i, (long) i,
                    70.0 + i, Instant.now()));
        }
        for (int i = 1; i <= fallos; i++) {
            muestras.add(Muestra.fallida(Escenario.CAMBIO_LISTO, 100 + i, "TIMEOUT de prueba", Instant.now()));
        }
        return ResultadoMedicion.de(muestras, "http://localhost:8080", exitos + fallos, 10000, 10);
    }

    @Test
    @DisplayName("Tabla del informe: encabezado y tres escenarios")
    void tablaInforme() throws Exception {
        ResultadoMedicion resultado = resultadoDeEjemplo(20, 0);
        Path destino = GeneradorInformes.escribirTabla(temporal, resultado);
        String texto = Files.readString(destino, StandardCharsets.UTF_8);

        assertTrue(texto.startsWith("Tabla 3. Registro de pruebas de propagacion de eventos."));
        assertTrue(texto.contains("Escenario"));
        assertTrue(texto.contains("Latencia media (ms)"));
        assertTrue(texto.contains("Exito (%)"));
        assertTrue(texto.contains("Pedido nuevo"));
        assertTrue(texto.contains("Cambio a PREPARANDO"));
        assertTrue(texto.contains("Cambio a LISTO"));
    }

    @Test
    @DisplayName("Resumen academico: usa los valores reales y no afirma mejoras en restaurantes")
    void resumenAcademico() throws Exception {
        ResultadoMedicion resultado = resultadoDeEjemplo(20, 0);
        Path destino = GeneradorInformes.escribirResumen(temporal, resultado);
        String texto = Files.readString(destino, StandardCharsets.UTF_8);

        assertTrue(texto.contains("20 pruebas"));
        assertTrue(texto.contains("mayor latencia media"));
        assertTrue(texto.contains("menor latencia media"));
        assertTrue(texto.contains("No se registraron fallos"));
        assertTrue(texto.contains("prototipo academico"));
        // No debe contener afirmaciones prohibidas.
        String minuscula = texto.toLowerCase();
        assertFalse(minuscula.contains("mejoro la eficiencia"));
        assertFalse(minuscula.contains("redujo tiempos reales"));
        assertFalse(minuscula.contains("optimizo el restaurante"));
        assertFalse(minuscula.contains("mejoro el rendimiento"));
    }

    @Test
    @DisplayName("Resumen academico con fallos: los registra y no inventa latencias")
    void resumenConFallos() throws Exception {
        ResultadoMedicion resultado = resultadoDeEjemplo(19, 1);
        Path destino = GeneradorInformes.escribirResumen(temporal, resultado);
        String texto = Files.readString(destino, StandardCharsets.UTF_8);

        assertTrue(texto.contains("fallidas"));
        assertTrue(texto.contains("excluidas del calculo de las metricas de latencia"));
    }

    @Test
    @DisplayName("Metodologia: describe entorno, escenarios, reloj y sondeo")
    void metodologia() throws Exception {
        ResultadoMedicion resultado = resultadoDeEjemplo(20, 0);
        Path destino = GeneradorInformes.escribirMetodologia(temporal, resultado);
        String texto = Files.readString(destino, StandardCharsets.UTF_8);

        assertTrue(texto.contains("RabbitMQ"));
        assertTrue(texto.contains("API Gateway"));
        assertTrue(texto.contains("System.nanoTime()"));
        assertTrue(texto.contains("sondeo"));
        assertTrue(texto.contains("20 muestras por escenario") || texto.contains("muestras por escenario"));
        assertTrue(texto.contains("porcentaje de exito"));
        assertTrue(texto.contains("prototipo academico"));
    }

    @Test
    @DisplayName("Escenario con mayor y menor latencia media")
    void extremosDeLatencia() {
        ResultadoMedicion resultado = resultadoDeEjemplo(20, 0);

        // PREPARANDO (51..70) es el mas rapido; PEDIDO_NUEVO (101..120) el mas lento.
        assertTrue(GeneradorInformes.mayorLatencia(resultado).isPresent());
        assertTrue(GeneradorInformes.menorLatencia(resultado).isPresent());
        assertTrue(GeneradorInformes.mayorLatencia(resultado).get() == Escenario.PEDIDO_NUEVO);
        assertTrue(GeneradorInformes.menorLatencia(resultado).get() == Escenario.CAMBIO_PREPARANDO);
    }
}
