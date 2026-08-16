package br.com.brew.brassia.container.domain;

import java.util.UUID;

/** 404 para "não existe" e para "não é seu", sem distinguir. */
public class UnknownContainerException extends RuntimeException {

    public UnknownContainerException(UUID id) {
        super("Contêiner " + id + " não encontrado.");
    }

    public UnknownContainerException(String value) {
        super("Nenhum contêiner responde por " + value + ".");
    }
}
