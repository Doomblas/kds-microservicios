package com.kds.metricas.modelo;

import java.time.Instant;

/**
 * Resultado individual de una prueba de propagacion.
 *
 * @param escenario      escenario medido
 * @param numeroPrueba   numero de iteracion (1..N)
 * @param pedidoId       id del pedido en ms-pedidos (correlacion con cocina)
 * @param numeroPedido   numero de pedido legible generado por ms-pedidos
 * @param ordenId        id de la orden equivalente en ms-display-cocina
 * @param exito          true si la propagacion fue detectada dentro del timeout
 * @param latenciaMs     latencia medida en milisegundos (null si la prueba fallo)
 * @param timestamp      instante en que se registro la muestra
 * @param detalle        detalle o mensaje de error (null si la prueba fue exitosa)
 */
public record Muestra(
        Escenario escenario,
        int numeroPrueba,
        Long pedidoId,
        String numeroPedido,
        Long ordenId,
        boolean exito,
        Double latenciaMs,
        Instant timestamp,
        String detalle
) {

    public static Muestra exitosa(Escenario escenario, int numeroPrueba, Long pedidoId, String numeroPedido,
                                  Long ordenId, double latenciaMs, Instant timestamp) {
        return new Muestra(escenario, numeroPrueba, pedidoId, numeroPedido, ordenId, true, latenciaMs, timestamp, null);
    }

    public static Muestra fallida(Escenario escenario, int numeroPrueba, String detalle, Instant timestamp) {
        return new Muestra(escenario, numeroPrueba, null, null, null, false, null, timestamp, detalle);
    }

    public static Muestra fallida(Escenario escenario, int numeroPrueba, Long pedidoId, String numeroPedido,
                                  Long ordenId, String detalle, Instant timestamp) {
        return new Muestra(escenario, numeroPrueba, pedidoId, numeroPedido, ordenId, false, null, timestamp, detalle);
    }
}
