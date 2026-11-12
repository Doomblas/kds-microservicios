package com.kds.metricas.salida;

import com.kds.metricas.modelo.Estadisticas;
import com.kds.metricas.modelo.Escenario;
import com.kds.metricas.modelo.Muestra;
import com.kds.metricas.modelo.ResultadoMedicion;

import java.io.PrintStream;

/**
 * Presentacion de los resultados por consola, con el formato pedido para el
 * informe academico y una tabla compacta final lista para copiar.
 */
public final class Consola {

    private final PrintStream out;

    public Consola(PrintStream out) {
        this.out = out;
    }

    public void separador() {
        out.println("==================================================");
    }

    public void cabecera(String titulo) {
        separador();
        out.println(titulo);
        separador();
    }

    public void linea(String texto) {
        out.println(texto);
    }

    public void vacio() {
        out.println();
    }

    /** Bloque estadistico por escenario. */
    public void bloqueEscenario(Escenario escenario, Estadisticas e) {
        out.println(escenario.etiqueta());
        out.println("Pruebas: " + e.pruebas());
        out.println("Exitos: " + e.exitos());
        out.println("Fallos: " + e.fallos());
        out.println("Exito: " + formato(e.exitoPct(), "%.2f") + " %");
        out.println("Latencia media: " + formato(e.media(), "%.2f") + " ms");
        out.println("Minima: " + formato(e.minimo(), "%.2f") + " ms");
        out.println("Maxima: " + formato(e.maximo(), "%.2f") + " ms");
        out.println("Mediana: " + formato(e.mediana(), "%.2f") + " ms");
        out.println("Desviacion estandar: " + formato(e.desviacion(), "%.2f") + " ms");
        out.println();
    }

    /** Tabla compacta final, lista para copiar al informe. */
    public void tablaResumen(ResultadoMedicion resultado) {
        out.println("Escenario | Pruebas | Latencia media (ms) | Exito (%)");
        out.println("----------+----------+----------------------+------------");
        for (Escenario escenario : ResultadoMedicion.ORDEN_ESCENARIOS) {
            Estadisticas e = resultado.de(escenario);
            out.println(String.format("%-9s | %-8d | %-20s | %s",
                    escenario.etiqueta(),
                    e.pruebas(),
                    formato(e.media(), "%.2f"),
                    formato(e.exitoPct(), "%.2f")));
        }
        out.println();
    }

    /** Detalle de las pruebas fallidas, si las hay. */
    public void detalleFallos(ResultadoMedicion resultado) {
        var fallidas = resultado.muestras().stream().filter(m -> !m.exito()).toList();
        if (fallidas.isEmpty()) {
            return;
        }
        out.println("Detalle de pruebas fallidas (" + fallidas.size() + "):");
        for (Muestra m : fallidas) {
            out.println("  - [" + m.escenario().etiqueta() + "] prueba " + m.numeroPrueba()
                    + " pedido=" + (m.pedidoId() == null ? "-" : m.pedidoId())
                    + ": " + m.detalle());
        }
        out.println();
    }

    public void error(String mensaje) {
        out.println("[ERROR] " + mensaje);
    }

    public void aviso(String mensaje) {
        out.println("[AVISO] " + mensaje);
    }

    public void ok(String mensaje) {
        out.println("[OK] " + mensaje);
    }

    private static String formato(Double valor, String patron) {
        return valor == null ? "n/d" : String.format(java.util.Locale.ROOT, patron, valor);
    }
}
