package com.kds.metricas.salida;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.kds.metricas.modelo.Estadisticas;
import com.kds.metricas.modelo.Escenario;
import com.kds.metricas.modelo.ResultadoMedicion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exportacion del resumen estadistico en JSON, pensada como fuente de datos
 * para la documentacion y para futuras graficas.
 */
public final class ExportadorJson {

    public static final String ARCHIVO = "latencias_resumen.json";

    private ExportadorJson() {
    }

    public static Path escribirResumen(Path directorio, ResultadoMedicion resultado) throws IOException {
        Path destino = directorio.resolve(ARCHIVO);
        ObjectMapper mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);

        Map<String, Object> raiz = new LinkedHashMap<>();
        raiz.put("herramienta", "metricas-kds");
        raiz.put("descripcion",
                "Medicion automatica de propagacion de eventos del KDS "
                        + "(Pedidos -> RabbitMQ -> Cocina y Cocina -> RabbitMQ -> Pedidos)");
        raiz.put("fechaEjecucion", LocalDateTime.now().atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        raiz.put("baseUrl", resultado.baseUrl());
        raiz.put("numPruebasPorEscenario", resultado.numPruebas());
        raiz.put("timeoutMs", resultado.timeoutMs());
        raiz.put("intervaloSondeoMs", resultado.pollIntervalMs());

        List<Map<String, Object>> escenarios = ResultadoMedicion.ORDEN_ESCENARIOS.stream()
                .map(escenario -> aMapa(escenario, resultado.de(escenario)))
                .toList();
        raiz.put("escenarios", escenarios);

        Files.writeString(destino, mapper.writeValueAsString(raiz), StandardCharsets.UTF_8);
        return destino;
    }

    static Map<String, Object> aMapa(Escenario escenario, Estadisticas e) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("escenario", escenario.etiqueta());
        mapa.put("pruebas", e.pruebas());
        mapa.put("exitos", e.exitos());
        mapa.put("fallos", e.fallos());
        mapa.put("exitoPct", redondear(e.exitoPct(), 2));
        mapa.put("latenciaMediaMs", redondear(e.media(), 3));
        mapa.put("minMs", redondear(e.minimo(), 3));
        mapa.put("maxMs", redondear(e.maximo(), 3));
        mapa.put("medianaMs", redondear(e.mediana(), 3));
        mapa.put("desviacionMs", redondear(e.desviacion(), 3));
        return mapa;
    }

    private static Double redondear(Double valor, int decimales) {
        if (valor == null) {
            return null;
        }
        double factor = Math.pow(10, decimales);
        return Math.round(valor * factor) / factor;
    }
}
