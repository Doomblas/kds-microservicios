package com.kds.metricas.salida;

import com.kds.metricas.modelo.Estadisticas;
import com.kds.metricas.modelo.Escenario;
import com.kds.metricas.modelo.ResultadoMedicion;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.CategoryChart;
import org.knowm.xchart.CategoryChartBuilder;
import org.knowm.xchart.CategorySeries;

import java.awt.Color;
import java.awt.Font;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Generacion de las graficas PNG del informe. Usa XChart (libreria Java
 *Headless-friendly) sobre los valores reales calculados por la medicion.
 */
public final class GeneradorGraficas {

    public static final String ARCHIVO_PROMEDIO = "latencias_promedio.png";
    public static final String ARCHIVO_MIN_MEDIA_MAX = "latencias_min_media_max.png";

    private static final int ANCHO = 1600;
    private static final int ALTO = 900;

    /** Azul sobrio usado en el informe academico. */
    private static final Color AZUL = new Color(0x2F, 0x5D, 0x8C);
    private static final Color AZUL_CLARO = new Color(0x7F, 0xA8, 0xCC);
    private static final Color GRIS = new Color(0x8A, 0x8A, 0x8A);

    private GeneradorGraficas() {
    }

    /**
     * Grafica obligatoria: barras con la latencia media de cada escenario.
     */
    public static Path graficaPromedio(Path directorio, ResultadoMedicion resultado) throws IOException {
        List<String> categorias = new ArrayList<>();
        List<Double> valores = new ArrayList<>();
        for (Escenario escenario : ResultadoMedicion.ORDEN_ESCENARIOS) {
            Estadisticas e = resultado.de(escenario);
            categorias.add(escenario.etiqueta());
            valores.add(e.media() == null ? 0.0 : e.media());
        }

        CategoryChartBuilder builder = new CategoryChartBuilder();
        builder.width = ANCHO;
        builder.height = ALTO;
        builder.title("Latencia media de propagacion de eventos del KDS");
        builder.xAxisTitle("Escenario de propagacion");
        builder.yAxisTitle("Latencia media (ms)");

        CategoryChart chart = builder.build();
        CategorySeries serie = chart.addSeries("Latencia media (ms)", categorias, valores);
        serie.setFillColor(AZUL);

        estilo(chart);
        chart.getStyler().setLegendVisible(false);

        Path destino = directorio.resolve(ARCHIVO_PROMEDIO);
        BitmapEncoder.saveBitmap(chart, destino.toString(), BitmapEncoder.BitmapFormat.PNG);
        return destino;
    }

    /**
     * Grafica secundaria: minima, media y maxima por escenario.
     */
    public static Path graficaMinMediaMax(Path directorio, ResultadoMedicion resultado) throws IOException {
        List<String> categorias = new ArrayList<>();
        List<Double> minimos = new ArrayList<>();
        List<Double> medias = new ArrayList<>();
        List<Double> maximos = new ArrayList<>();
        for (Escenario escenario : ResultadoMedicion.ORDEN_ESCENARIOS) {
            Estadisticas e = resultado.de(escenario);
            categorias.add(escenario.etiqueta());
            minimos.add(e.minimo() == null ? 0.0 : e.minimo());
            medias.add(e.media() == null ? 0.0 : e.media());
            maximos.add(e.maximo() == null ? 0.0 : e.maximo());
        }

        CategoryChartBuilder builder = new CategoryChartBuilder();
        builder.width = ANCHO;
        builder.height = ALTO;
        builder.title("Dispersion de la latencia de propagacion de eventos del KDS");
        builder.xAxisTitle("Escenario de propagacion");
        builder.yAxisTitle("Latencia (ms)");

        CategoryChart chart = builder.build();
        CategorySeries serieMin = chart.addSeries("Minima", categorias, minimos);
        serieMin.setFillColor(AZUL_CLARO);
        CategorySeries serieMedia = chart.addSeries("Media", categorias, medias);
        serieMedia.setFillColor(AZUL);
        CategorySeries serieMax = chart.addSeries("Maxima", categorias, maximos);
        serieMax.setFillColor(GRIS);

        estilo(chart);
        chart.getStyler().setLegendVisible(true);
        chart.getStyler().setLegendPosition(org.knowm.xchart.style.Styler.LegendPosition.OutsideS);
        chart.getStyler().setLegendLayout(org.knowm.xchart.style.Styler.LegendLayout.Horizontal);

        Path destino = directorio.resolve(ARCHIVO_MIN_MEDIA_MAX);
        BitmapEncoder.saveBitmap(chart, destino.toString(), BitmapEncoder.BitmapFormat.PNG);
        return destino;
    }

    /** Estilo sobrio y legible para impresion academica. */
    private static void estilo(CategoryChart chart) {
        chart.getStyler().setChartBackgroundColor(Color.WHITE);
        chart.getStyler().setPlotBackgroundColor(Color.WHITE);
        chart.getStyler().setAntiAlias(true);
        chart.getStyler().setChartTitleFont(new Font("SansSerif", Font.BOLD, 30));
        chart.getStyler().setAxisTitleFont(new Font("SansSerif", Font.PLAIN, 20));
        chart.getStyler().setAxisTickLabelsFont(new Font("SansSerif", Font.PLAIN, 16));
        chart.getStyler().setLabelsFont(new Font("SansSerif", Font.BOLD, 18));
        chart.getStyler().setLegendFont(new Font("SansSerif", Font.PLAIN, 16));
        chart.getStyler().setLabelsVisible(true);
        chart.getStyler().setLabelsPosition(0.985);
        chart.getStyler().setYAxisDecimalPattern("0.##");
        chart.getStyler().setChartFontColor(new Color(0x22, 0x22, 0x22));
        chart.getStyler().setPlotGridLinesVisible(true);
        chart.getStyler().setPlotGridLinesColor(new Color(0xDD, 0xDD, 0xDD));
    }
}
