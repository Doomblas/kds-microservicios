package com.kds.metricas.modelo;

/**
 * Escenarios de propagacion de eventos medidos por la herramienta.
 * La etiqueta es la que se usa en la salida de consola, CSV y en el informe.
 */
public enum Escenario {

    PEDIDO_NUEVO("Pedido nuevo"),
    CAMBIO_PREPARANDO("Cambio a PREPARANDO"),
    CAMBIO_LISTO("Cambio a LISTO");

    private final String etiqueta;

    Escenario(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
