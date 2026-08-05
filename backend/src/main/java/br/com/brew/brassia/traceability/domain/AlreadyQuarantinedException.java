package br.com.brew.brassia.traceability.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Já existe quarentena aberta para o nó (FDS-002).
 *
 * <p>Abrir a segunda partiria a investigação em duas, e liberar uma delas daria a impressão de que
 * o lote foi liberado. A recusa devolve a quarentena vigente para que quem pediu vá até ela.
 */
public final class AlreadyQuarantinedException extends RuntimeException {

    private final transient UUID quarantineId;

    public AlreadyQuarantinedException(UUID quarantineId) {
        super("já existe quarentena aberta para este nó");
        this.quarantineId = Objects.requireNonNull(quarantineId);
    }

    public UUID quarantineId() {
        return quarantineId;
    }
}
