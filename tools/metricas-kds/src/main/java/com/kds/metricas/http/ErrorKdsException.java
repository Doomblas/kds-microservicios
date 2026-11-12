package com.kds.metricas.http;

/**
 * Error funcional de la API del KDS: respuesta HTTP no exitosa o cuerpo
 * inesperado. El mensaje incluye el codigo y un extracto de la respuesta para
 * poder diagnosticar sin repetir la llamada.
 */
public class ErrorKdsException extends RuntimeException {

    private final int status;
    private final String cuerpo;

    public ErrorKdsException(int status, String mensaje, String cuerpo) {
        super(mensaje);
        this.status = status;
        this.cuerpo = cuerpo;
    }

    public int status() {
        return status;
    }

    public String cuerpo() {
        return cuerpo;
    }
}
