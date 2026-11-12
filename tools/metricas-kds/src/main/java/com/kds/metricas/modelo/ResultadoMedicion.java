package com.kds.metricas.modelo;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Resultado completo de una corrida: las muestras individuales y las
 * estadisticas agregadas por escenario, conservando siempre el orden
 * canonico de los tres escenarios del informe.
 */
public record ResultadoMedicion(
        List<Muestra> muestras,
        Map<Escenario, Estadisticas> estadisticas,
        String baseUrl,
        int numPruebas,
        long timeoutMs,
        long pollIntervalMs
) {

    public static final List<Escenario> ORDEN_ESCENARIOS =
            List.of(Escenario.PEDIDO_NUEVO, Escenario.CAMBIO_PREPARANDO, Escenario.CAMBIO_LISTO);

    public static ResultadoMedicion de(List<Muestra> muestras, String baseUrl, int numPruebas,
                                       long timeoutMs, long pollIntervalMs) {
        Map<Escenario, Estadisticas> stats = new EnumMap<>(Escenario.class);
        for (Escenario escenario : ORDEN_ESCENARIOS) {
            List<Muestra> delEscenario = muestras.stream()
                    .filter(m -> m.escenario() == escenario)
                    .toList();
            stats.put(escenario, Estadisticas.calcular(delEscenario));
        }
        return new ResultadoMedicion(List.copyOf(muestras), stats, baseUrl, numPruebas, timeoutMs, pollIntervalMs);
    }

    public Estadisticas de(Escenario escenario) {
        return estadisticas.getOrDefault(escenario, Estadisticas.calcular(List.of()));
    }
}
