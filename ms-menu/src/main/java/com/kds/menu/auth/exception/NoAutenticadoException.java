package com.kds.menu.auth.exception;

public class NoAutenticadoException extends RuntimeException {

    public NoAutenticadoException() {
        super("La sesión no es válida o expiró");
    }
}
