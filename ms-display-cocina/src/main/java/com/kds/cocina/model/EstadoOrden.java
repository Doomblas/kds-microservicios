package com.kds.cocina.model;

/**
 * Estados permitidos para una orden de cocina en la version 1.
 * El flujo normal es PENDIENTE -> PREPARANDO -> LISTO.
 */
public enum EstadoOrden {

    PENDIENTE,
    PREPARANDO,
    LISTO;

    public boolean permiteTransicionA(EstadoOrden destino) {
        return switch (this) {
            case PENDIENTE -> destino == PREPARANDO;
            case PREPARANDO -> destino == LISTO;
            case LISTO -> false;
        };
    }
}
