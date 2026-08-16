package br.com.brew.brassia.distribution.domain;

import java.util.UUID;

/** 404 para "não existe" e para "não é da sua casa", sem distinguir. */
public class UnknownLoadException extends RuntimeException {

    public UnknownLoadException(UUID id) {
        super("Carga " + id + " não encontrada.");
    }
}
