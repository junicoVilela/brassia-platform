package br.com.brew.brassia.blend.domain;

import java.util.UUID;

/** Operação que não existe nesta cervejaria. */
public final class UnknownBlendOperationException extends RuntimeException {

    public UnknownBlendOperationException(UUID operationId) {
        super("operação de blend desconhecida: " + operationId);
    }
}
