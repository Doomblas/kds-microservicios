package com.kds.menu.exception;

public class CategoriaNoEncontradaException extends RuntimeException {

    public CategoriaNoEncontradaException(Long id) {
        super("Categoria no encontrada con id: " + id);
    }
}
