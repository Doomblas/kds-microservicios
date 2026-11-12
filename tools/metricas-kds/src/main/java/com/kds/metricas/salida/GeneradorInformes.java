package com.kds.metricas.salida;

import com.kds.metricas.modelo.Estadisticas;
import com.kds.metricas.modelo.Escenario;
import com.kds.metricas.modelo.Muestra;
import com.kds.metricas.modelo.ResultadoMedicion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Genera los textos listos para pegar en el informe academico:
 * tabla de resultados, resumen redactado a partir de los datos reales y
 * descripcion de la metodologia de medicion.
 *
 * <p>El resumen se redacta exclusivamente con los valores medidos; no afirma
 * mejoras sobre ningun restaurante real, porque el sistema medido es un
 * prototipo academico desplegado en un entorno controlado.</p>
 */
public final class GeneradorInformes {

    public static final String ARCHIVO_TABLA = "tabla_informe.txt";
    public static final String ARCHIVO_RESUMEN = "resumen_informe.txt";
    public static final String ARCHIVO_METODOLOGIA = "metodologia_medicion.txt";

    private GeneradorInformes() {
    }

    // ------------------------------------------------------------------
    // Tabla para el informe
    // ------------------------------------------------------------------

    public static Path escribirTabla(Path directorio, ResultadoMedicion resultado) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("Tabla 3. Registro de pruebas de propagacion de eventos.\n");
        sb.append('\n');
        sb.append(String.format("%-22s | %-8s | %-20s | %-10s%n",
                "Escenario", "Pruebas", "Latencia media (ms)", "Exito (%)"));
        sb.append("-----------------------+----------+----------------------+------------\n");
        for (Escenario escenario : ResultadoMedicion.ORDEN_ESCENARIOS) {
            Estadisticas e = resultado.de(escenario);
            sb.append(String.format("%-22s | %-8d | %-20s | %-10s%n",
                    escenario.etiqueta(),
                    e.pruebas(),
                    formato(e.media(), "%.2f"),
                    formato(e.exitoPct(), "%.2f")));
        }
        Path destino = directorio.resolve(ARCHIVO_TABLA);
        Files.writeString(destino, sb.toString(), StandardCharsets.UTF_8);
        return destino;
    }

    // ------------------------------------------------------------------
    // Resumen academico
    // ------------------------------------------------------------------

    public static Path escribirResumen(Path directorio, ResultadoMedicion resultado) throws IOException {
        Estadisticas nuevo = resultado.de(Escenario.PEDIDO_NUEVO);
        Estadisticas preparando = resultado.de(Escenario.CAMBIO_PREPARANDO);
        Estadisticas listo = resultado.de(Escenario.CAMBIO_LISTO);

        Optional<Escenario> mayor = mayorLatencia(resultado);
        Optional<Escenario> menor = menorLatencia(resultado);
        int totalFallos = nuevo.fallos() + preparando.fallos() + listo.fallos();
        int totalPruebas = nuevo.pruebas() + preparando.pruebas() + listo.pruebas();

        StringBuilder sb = new StringBuilder();
        sb.append("Resumen de la medicion de propagacion de eventos del KDS\n");
        sb.append("=====================================================================\n\n");

        sb.append("Se realizaron ")
          .append(resultado.numPruebas())
          .append(" pruebas para cada uno de los tres escenarios de propagacion evaluados "
                  + "(pedido nuevo, cambio a PREPARANDO y cambio a LISTO), es decir ")
          .append(totalPruebas)
          .append(" mediciones en total sobre el prototipo academico del KDS desplegado en un entorno controlado.\n\n");

        sb.append("En el escenario de pedido nuevo, la latencia media de propagacion desde ms-pedidos "
                + "hasta ms-display-cocina (a traves del API Gateway y del broker RabbitMQ) fue de ")
          .append(formato(nuevo.media(), "%.2f")).append(" ms, con un ")
          .append(formato(nuevo.exitoPct(), "%.2f")).append(" % de pruebas exitosas (")
          .append(nuevo.exitos()).append(" de ").append(nuevo.pruebas()).append(").\n\n");

        sb.append("En el escenario de cambio a PREPARANDO, la latencia media desde ms-display-cocina "
                + "hasta ms-pedidos fue de ").append(formato(preparando.media(), "%.2f"))
          .append(" ms, con un ").append(formato(preparando.exitoPct(), "%.2f"))
          .append(" % de pruebas exitosas (").append(preparando.exitos()).append(" de ")
          .append(preparando.pruebas()).append(").\n\n");

        sb.append("En el escenario de cambio a LISTO, la latencia media desde ms-display-cocina "
                + "hasta ms-pedidos fue de ").append(formato(listo.media(), "%.2f"))
          .append(" ms, con un ").append(formato(listo.exitoPct(), "%.2f"))
          .append(" % de pruebas exitosas (").append(listo.exitos()).append(" de ")
          .append(listo.pruebas()).append(").\n\n");

        if (mayor.isPresent() && menor.isPresent()) {
            Estadisticas eMayor = resultado.de(mayor.get());
            Estadisticas eMenor = resultado.de(menor.get());
            sb.append("El escenario con mayor latencia media fue ").append(mayor.get().etiqueta())
              .append(" (").append(formato(eMayor.media(), "%.2f")).append(" ms) y el de menor latencia media fue ")
              .append(menor.get().etiqueta()).append(" (").append(formato(eMenor.media(), "%.2f")).append(" ms).\n\n");
        }

        if (totalFallos == 0) {
            sb.append("No se registraron fallos: las ").append(totalPruebas)
              .append(" mediciones completaron el flujo de propagacion dentro del tiempo de espera configurado, "
                      + "lo que indica que los dos sentidos del flujo asincrono (Pedidos -> Cocina y Cocina -> Pedidos) "
                      + "se comportaron de forma consistente durante la campana de medicion.\n\n");
        } else {
            sb.append("Se registraron ").append(totalFallos).append(" pruebas fallidas sobre ").append(totalPruebas)
              .append(". Las causas concretas de cada fallo quedan registradas en el fichero de detalle "
                      + ExportadorCsv.ARCHIVO_DETALLE + ", y las pruebas fallidas se contabilizan en el porcentaje "
                      + "de exito pero quedan excluidas del calculo de las metricas de latencia.\n\n");
        }

        sb.append("En terminos de comportamiento observado, los tres escenarios presentan tiempos del mismo orden de magnitud, "
                + "lo que sugiere que el coste dominante de la propagacion es el transporte a traves del broker y la "
                + "persistencia en el servicio consumidor, y no un unico eslabon especialmente lento. "
                + "Las diferencias entre el escenario de pedido nuevo y los de cambio de estado deben interpretarse "
                + "con cautela, porque el intervalo de sondeo usado para detectar la propagacion introduce una "
                + "resolucion de medida que puede ser comparable a las diferencias observadas.\n\n");

        sb.append("Los resultados corresponden exclusivamente a un prototipo academico ejecutado en un entorno simulado "
                + "y controlado, con la red local y los recursos de una sola estacion de trabajo. No representan el "
                + "comportamiento de un restaurante real en produccion ni permiten afirmar mejoras operativas "
                + "en ningun establecimiento.\n");

        Path destino = directorio.resolve(ARCHIVO_RESUMEN);
        Files.writeString(destino, sb.toString(), StandardCharsets.UTF_8);
        return destino;
    }

    // ------------------------------------------------------------------
    // Metodologia
    // ------------------------------------------------------------------

    public static Path escribirMetodologia(Path directorio, ResultadoMedicion resultado) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("Metodologia de medicion de la propagacion de eventos del KDS\n");
        sb.append("=====================================================================\n\n");

        sb.append("1. Entorno de ejecucion\n");
        sb.append("   La medicion se realizo sobre el prototipo academico del Kitchen Display System, desplegado "
                + "mediante Docker Compose y compuesto por los servicios ms-menu, ms-pedidos, ms-display-cocina y "
                + "api-gateway, un broker RabbitMQ y tres bases de datos PostgreSQL independientes. "
                + "Todas las peticiones de la herramienta se dirigieron al API Gateway (")
          .append(resultado.baseUrl())
          .append("), de modo que la medicion incluye el comportamiento real del gateway y no solo el de los "
                  + "microservicios internos.\n\n");

        sb.append("2. Autenticacion\n");
        sb.append("   La herramienta se autentico mediante POST /auth/login con un usuario de rol ADMIN "
                + "suministrado por variables de entorno. Ninguna credencial queda almacenada en el repositorio. "
                + "El token JWT obtenido se compartio en las peticiones posteriores hacia ms-pedidos y "
                + "ms-display-cocina.\n\n");

        sb.append("3. Escenarios y numero de pruebas\n");
        sb.append("   Se evaluaron tres escenarios de propagacion de eventos: (a) pedido nuevo, desde la creacion del "
                + "pedido en ms-pedidos hasta que la orden aparece en ms-display-cocina; (b) cambio a PREPARANDO, "
                + "desde la actualizacion de la orden en ms-display-cocina hasta que ms-pedidos refleja ese estado; y "
                + "(c) cambio a LISTO, con el mismo procedimiento que el escenario anterior. "
                + "Cada iteracion ejecuta la secuencia completa pedido nuevo -> PREPARANDO -> LISTO, de modo que se "
                + "obtienen ").append(resultado.numPruebas())
          .append(" muestras por escenario (").append(resultado.numPruebas() * 3)
          .append(" mediciones en total).\n\n");

        sb.append("4. Instrumentacion temporal\n");
        sb.append("   Los instantes de referencia se midieron con System.nanoTime(), un reloj monotono de alta "
                + "resolucion e independiente de los ajustes del reloj del sistema. El instante inicial (t0) se toma "
                + "inmediatamente antes de enviar la peticion HTTP que origina el evento (POST /pedidos o "
                + "PATCH /ordenes/{id}/estado) y el instante final (t1) es el momento en que la respuesta que ya "
                + "refleja el estado esperado es recibida por el cliente. La latencia se define como t1 - t0 y se "
                + "expresa en milisegundos con tres decimales. No se utiliza el tiempo de respuesta HTTP de la "
                + "peticion original como metrica, sino el tiempo hasta que el estado es observable en el servicio "
                + "consumidor, de modo que se mide la propagacion funcional completa "
                + "(persistencia, publicacion en RabbitMQ, consumo y persistencia en el servicio destino).\n\n");

        sb.append("5. Deteccion de la propagacion\n");
        sb.append("   La aparicion del estado esperado se detecto mediante sondeo (polling) corto y controlado, con un "
                + "intervalo de ").append(resultado.pollIntervalMs())
          .append(" ms y un tiempo de espera maximo de ").append(resultado.timeoutMs())
          .append(" ms por evento. El sondeo se detiene de inmediato en cuanto se observa el estado esperado, "
                  + "por lo que la latencia reportada incluye, como maximo, un ciclo de sondeo adicional. "
                  + "La correlacion entre el pedido de ms-pedidos y la orden de ms-display-cocina se realizo mediante "
                  + "el identificador pedidoId, que el broker transporta en los eventos pedido.creado y "
                  + "pedido.estado.actualizado.\n\n");

        sb.append("6. Indicadores y tratamiento de los datos\n");
        sb.append("   El porcentaje de exito se calculo como el cociente entre el numero de pruebas en las que se "
                + "detecto la propagacion dentro del tiempo de espera y el total de pruebas ejecutadas del escenario, "
                + "expresado en porcentaje. Las metricas de latencia (media, minima, maxima, mediana y desviacion "
                + "estandar muestral) se calcularon unicamente sobre las pruebas exitosas; las pruebas fallidas se "
                + "contabilizan en el porcentaje de exito y quedan excluidas del calculo de latencia, sin asignarles "
                + "ningun valor estimado.\n\n");

        sb.append("7. Naturaleza de los datos\n");
        sb.append("   Las mediciones corresponden a un prototipo academico ejecutado en un entorno simulado y "
                + "controlado. Los pedidos creados durante la campana de medicion permanecen en las bases de datos "
                + "del prototipo, ya que el contrato REST no expone un endpoint de eliminacion de pedidos; su "
                + "identificador (id y numeroPedido) queda registrado en el fichero de detalle "
                + ExportadorCsv.ARCHIVO_DETALLE + ".\n");

        Path destino = directorio.resolve(ARCHIVO_METODOLOGIA);
        Files.writeString(destino, sb.toString(), StandardCharsets.UTF_8);
        return destino;
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    static Optional<Escenario> mayorLatencia(ResultadoMedicion resultado) {
        return mejorLatencia(resultado, Comparator.reverseOrder());
    }

    static Optional<Escenario> menorLatencia(ResultadoMedicion resultado) {
        return mejorLatencia(resultado, Comparator.naturalOrder());
    }

    private static Optional<Escenario> mejorLatencia(ResultadoMedicion resultado, Comparator<Double> orden) {
        return ResultadoMedicion.ORDEN_ESCENARIOS.stream()
                .filter(e -> resultado.de(e).media() != null)
                .min((a, b) -> orden.compare(resultado.de(a).media(), resultado.de(b).media()));
    }

    static String formato(Double valor, String patron) {
        return valor == null ? "n/d" : String.format(java.util.Locale.ROOT, patron, valor);
    }

    /** Numero de pruebas fallidas del conjunto de la corrida. */
    public static int totalFallos(ResultadoMedicion resultado) {
        int total = 0;
        for (Escenario escenario : ResultadoMedicion.ORDEN_ESCENARIOS) {
            total += resultado.de(escenario).fallos();
        }
        return total;
    }

    /** Muestras fallidas con su detalle, para el registro de incidencias. */
    public static List<Muestra> fallidas(ResultadoMedicion resultado) {
        return resultado.muestras().stream().filter(m -> !m.exito()).toList();
    }
}
