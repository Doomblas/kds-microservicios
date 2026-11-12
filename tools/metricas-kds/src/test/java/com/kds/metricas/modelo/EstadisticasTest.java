package com.kds.metricas.modelo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EstadisticasTest {

    private static Muestra ok(Escenario escenario, double latencia) {
        return Muestra.exitosa(escenario, 1, 1L, "ORD-1", 1L, latencia, Instant.now());
    }

    private static Muestra ko(Escenario escenario) {
        return Muestra.fallida(escenario, 1, "TIMEOUT", Instant.now());
    }

    @Test
    @DisplayName("Lista vacia: no inventa valores de latencia")
    void listaVacia() {
        Estadisticas e = Estadisticas.calcular(List.of());

        assertEquals(0, e.pruebas());
        assertEquals(0, e.exitos());
        assertEquals(0, e.fallos());
        assertEquals(0.0, e.exitoPct(), 1e-9);
        assertNull(e.media());
        assertNull(e.minimo());
        assertNull(e.maximo());
        assertNull(e.mediana());
        assertNull(e.desviacion());
        assertTrue(!e.tieneLatencias());
    }

    @Test
    @DisplayName("Lista nula se trata como lista vacia")
    void listaNula() {
        Estadisticas e = Estadisticas.calcular(null);

        assertEquals(0, e.pruebas());
        assertNull(e.media());
    }

    @Test
    @DisplayName("Media calculada sobre todas las muestras exitosas")
    void media() {
        List<Muestra> muestras = List.of(
                ok(Escenario.PEDIDO_NUEVO, 10.0),
                ok(Escenario.PEDIDO_NUEVO, 20.0),
                ok(Escenario.PEDIDO_NUEVO, 30.0));

        Estadisticas e = Estadisticas.calcular(muestras);

        assertEquals(3, e.pruebas());
        assertEquals(3, e.exitos());
        assertEquals(0, e.fallos());
        assertEquals(20.0, e.media(), 1e-9);
        assertEquals(10.0, e.minimo(), 1e-9);
        assertEquals(30.0, e.maximo(), 1e-9);
    }

    @Test
    @DisplayName("Mediana impar y par")
    void mediana() {
        List<Double> impar = new ArrayList<>(List.of(5.0, 1.0, 3.0));
        CollectionsHelper.orden(impar);
        assertEquals(3.0, Estadisticas.mediana(impar), 1e-9);

        List<Double> par = new ArrayList<>(List.of(4.0, 1.0, 3.0, 2.0));
        CollectionsHelper.orden(par);
        assertEquals(2.5, Estadisticas.mediana(par), 1e-9);
    }

    @Test
    @DisplayName("Desviacion estandar muestral")
    void desviacion() {
        // Poblacion {2,4,4,4,5,5,7,9}: media 5, varianza muestral 32/7, sd ~2.13809
        List<Double> valores = List.of(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0);
        double media = valores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        double sd = Estadisticas.desviacionEstandar(valores, media);

        assertEquals(2.13809, sd, 1e-4);
    }

    @Test
    @DisplayName("Una sola muestra no tiene dispersion observable")
    void desviacionConUnaMuestra() {
        assertEquals(0.0, Estadisticas.desviacionEstandar(List.of(7.5), 7.5), 1e-9);
    }

    @Test
    @DisplayName("Porcentaje de exito cuenta los fallos")
    void porcentajeExito() {
        List<Muestra> muestras = List.of(
                ok(Escenario.PEDIDO_NUEVO, 10.0),
                ok(Escenario.PEDIDO_NUEVO, 12.0),
                ok(Escenario.PEDIDO_NUEVO, 14.0),
                ok(Escenario.PEDIDO_NUEVO, 16.0),
                ko(Escenario.PEDIDO_NUEVO));

        Estadisticas e = Estadisticas.calcular(muestras);

        assertEquals(5, e.pruebas());
        assertEquals(4, e.exitos());
        assertEquals(1, e.fallos());
        assertEquals(80.0, e.exitoPct(), 1e-9);
        // La media ignora la prueba fallida.
        assertEquals(13.0, e.media(), 1e-9);
    }

    @Test
    @DisplayName("Si todas las pruebas fallan no hay metricas de latencia")
    void todasFallidas() {
        List<Muestra> muestras = List.of(
                ko(Escenario.PEDIDO_NUEVO),
                ko(Escenario.PEDIDO_NUEVO));

        Estadisticas e = Estadisticas.calcular(muestras);

        assertEquals(2, e.pruebas());
        assertEquals(0, e.exitos());
        assertEquals(2, e.fallos());
        assertEquals(0.0, e.exitoPct(), 1e-9);
        assertNull(e.media());
        assertNull(e.desviacion());
    }

    @Test
    @DisplayName("Agrregacion por escenario mantiene el orden canonico")
    void agregacionPorEscenario() {
        List<Muestra> muestras = List.of(
                ok(Escenario.PEDIDO_NUEVO, 10.0),
                ok(Escenario.CAMBIO_PREPARANDO, 20.0),
                ok(Escenario.CAMBIO_LISTO, 30.0),
                ko(Escenario.CAMBIO_LISTO));

        ResultadoMedicion resultado = ResultadoMedicion.de(muestras, "http://localhost:8080", 1, 10000, 10);

        assertEquals(3, ResultadoMedicion.ORDEN_ESCENARIOS.size());
        assertEquals(Escenario.PEDIDO_NUEVO, ResultadoMedicion.ORDEN_ESCENARIOS.get(0));
        assertEquals(Escenario.CAMBIO_PREPARANDO, ResultadoMedicion.ORDEN_ESCENARIOS.get(1));
        assertEquals(Escenario.CAMBIO_LISTO, ResultadoMedicion.ORDEN_ESCENARIOS.get(2));

        assertEquals(10.0, resultado.de(Escenario.PEDIDO_NUEVO).media(), 1e-9);
        assertEquals(20.0, resultado.de(Escenario.CAMBIO_PREPARANDO).media(), 1e-9);
        assertEquals(1, resultado.de(Escenario.CAMBIO_LISTO).exitos());
        assertEquals(1, resultado.de(Escenario.CAMBIO_LISTO).fallos());
        assertEquals(50.0, resultado.de(Escenario.CAMBIO_LISTO).exitoPct(), 1e-9);
    }

    /** Ayudante para ordenar listas inmutables en las pruebas. */
    static final class CollectionsHelper {
        private CollectionsHelper() {
        }

        static void orden(List<Double> valores) {
            java.util.Collections.sort(valores);
        }
    }
}
