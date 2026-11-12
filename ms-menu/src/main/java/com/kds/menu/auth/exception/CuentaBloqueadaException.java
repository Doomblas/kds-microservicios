package com.kds.menu.auth.exception;

public class CuentaBloqueadaException extends RuntimeException {

    public CuentaBloqueadaException() {
        super("Tu cuenta está bloqueada. Contacta a un administrador.");
    }
}
