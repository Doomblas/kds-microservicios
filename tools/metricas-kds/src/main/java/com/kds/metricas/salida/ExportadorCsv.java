package com.kds.metricas.salida;

import com.kds.metricas.modelo.Estadisticas;
import com.kds.metricas.modelo.Escenario;
import com.kds.metricas.modelo.Muestra;
import com.kds.metricas.modelo.ResultadoMedicion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Exportacion de los resultados en CSV: un fichero con el detalle de cada
 * muestra y otro con el resumen estadistico por escenario.
 */
public final class ExportadorCsv {

    public static final String ARCHIVO_DETALLE = "latencias_detalle.csv";
    public static final String ARCHIVO_RESUMEN = "latencias_resumen.csv";

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    private ExportadorCsv() {
    }

    public static Path escribirDetalle(Path directorio, List<Muestra> muestras) throws IOException {
        Path destino = directorio.resolve(ARCHIVO_DETALLE);
        StringBuilder sb = new StringBuilder();
        sb.append("escenario,prueba,pedido_id,numero_pedido,exito,latencia_ms,timestamp,detalle\n");
        for (Muestra m : muestras) {
            sb.append(csv(m.escenario().etiqueta())).append(',')
              .append(m.numeroPrueba()).append(',')
              .append(m.pedidoId() == null ? "" : m.pedidoId()).append(',')
              .append(csv(m.numeroPedido() == null ? "" : m.numeroPedido())).append(',')
              .append(m.exito()).append(',')
              .append(m.exito() && m.latenciaMs() != null ? String.format(java.util.Locale.ROOT, "%.3f", m.latenciaMs()) : "")
              .append(',')
              .append(csv(timestamp(m))).append(',')
              .append(csv(m.detalle() == null ? "" : m.detalle()))
              .append('\n');
        }
        Files.writeString(destino, sb.toString(), StandardCharsets.UTF_8);
        return destino;
    }

    public static Path escribirResumen(Path directorio, ResultadoMedicion resultado) throws IOException {
        Path destino = directorio.resolve(ARCHIVO_RESUMEN);
        StringBuilder sb = new StringBuilder();
        sb.append("escenario,pruebas,exitos,fallos,exito_pct,latencia_media_ms,min_ms,max_ms,mediana_ms,desviacion_ms\n");
        for (Escenario escenario : ResultadoMedicion.ORDEN_ESCENARIOS) {
            Estadisticas e = resultado.de(escenario);
            sb.append(csv(escenario.etiqueta())).append(',')
              .append(e.pruebas()).append(',')
              .append(e.exitos()).append(',')
              .append(e.fallos()).append(',')
              .append(String.format(java.util.Locale.ROOT, "%.2f", e.exitoPct())).append(',')
              .append(numero(e.media())).append(',')
              .append(numero(e.minimo())).append(',')
              .append(numero(e.maximo())).append(',')
              .append(numero(e.mediana())).append(',')
              .append(numero(e.desviacion()))
              .append('\n');
        }
        Files.writeString(destino, sb.toString(), StandardCharsets.UTF_8);
        return destino;
    }

    static String numero(Double valor) {
        return valor == null ? "" : String.format(java.util.Locale.ROOT, "%.3f", valor);
    }

    static String timestamp(Muestra m) {
        return TIMESTAMP.format(m.timestamp().atZone(ZoneId.systemDefault()));
    }

    /** Escapa un valor para CSV (comillas dobles y saltos de linea). */
    static String csv(String valor) {
        if (valor == null) {
            return "";
        }
        boolean necesitaComillas = valor.contains(",") || valor.contains("\"")
                || valor.contains("\n") || valor.contains("\r");
        String escapado = valor.replace("\"", "\"\"");
        return necesitaComillas ? "\"" + escapado + "\"" : escapado;
    }
}
