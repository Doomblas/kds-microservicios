package com.kds.metricas.modelo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Estadisticas agregadas de un escenario.
 *
 * <p>Las metricas de latencia (media, minima, maxima, mediana y desviacion
 * estandar) se calculan EXCLUSIVAMENTE sobre las muestras exitosas. Si no hay
 * ninguna muestra exitosa, esas metricas quedan en {@code null} (no se inventa
 * ningun valor). El porcentaje de exito, en cambio, se calcula sobre el total
 * de pruebas ejecutadas, incluidas las fallidas.</p>
 *
 * @param pruebas     numero total de pruebas ejecutadas
 * @param exitos      numero de pruebas en las que se detecto la propagacion
 * @param fallos      numero de pruebas fallidas
 * @param exitoPct    porcentaje de exito (0..100)
 * @param media       latencia media en ms (null si no hay exitos)
 * @param minimo      latencia minima en ms (null si no hay exitos)
 * @param maximo      latencia maxima en ms (null si no hay exitos)
 * @param mediana     latencia mediana en ms (null si no hay exitos)
 * @param desviacion  desviacion estandar muestral en ms (null si no hay exitos)
 */
public record Estadisticas(
        int pruebas,
        int exitos,
        int fallos,
        double exitoPct,
        Double media,
        Double minimo,
        Double maximo,
        Double mediana,
        Double desviacion
) {

    /** Calcula las estadisticas de un conjunto de muestras (posiblemente vacio). */
    public static Estadisticas calcular(List<Muestra> muestras) {
        if (muestras == null || muestras.isEmpty()) {
            return new Estadisticas(0, 0, 0, 0.0, null, null, null, null, null);
        }

        int total = muestras.size();
        int exitos = (int) muestras.stream().filter(Muestra::exito).count();
        int fallos = total - exitos;
        double exitoPct = (total == 0) ? 0.0 : (exitos * 100.0) / total;

        List<Double> latencias = new ArrayList<>();
        for (Muestra m : muestras) {
            if (m.exito() && m.latenciaMs() != null) {
                latencias.add(m.latenciaMs());
            }
        }

        if (latencias.isEmpty()) {
            return new Estadisticas(total, exitos, fallos, exitoPct, null, null, null, null, null);
        }

        Collections.sort(latencias);
        double media = latencias.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double minimo = latencias.get(0);
        double maximo = latencias.get(latencias.size() - 1);
        double mediana = mediana(latencias);
        double desviacion = desviacionEstandar(latencias, media);

        return new Estadisticas(total, exitos, fallos, exitoPct, media, minimo, maximo, mediana, desviacion);
    }

    /** Mediana: valor central para cardinalidad impar, promedio de los dos centrales si es par. */
    static double mediana(List<Double> ordenadas) {
        int n = ordenadas.size();
        if (n == 0) {
            return 0.0;
        }
        if (n % 2 == 1) {
            return ordenadas.get(n / 2);
        }
        return (ordenadas.get(n / 2 - 1) + ordenadas.get(n / 2)) / 2.0;
    }

    /**
     * Desviacion estandar muestral (denominador n-1). Con una sola muestra no
     * hay dispersion observable, por lo que se devuelve 0.0.
     */
    static double desviacionEstandar(List<Double> valores, double media) {
        int n = valores.size();
        if (n < 2) {
            return 0.0;
        }
        double sumaCuadrados = 0.0;
        for (Double v : valores) {
            double d = v - media;
            sumaCuadrados += d * d;
        }
        return Math.sqrt(sumaCuadrados / (n - 1));
    }

    public boolean tieneLatencias() {
        return media != null;
    }

    public OptionalDouble mediaOpcional() {
        return media == null ? OptionalDouble.empty() : OptionalDouble.of(media);
    }
}
