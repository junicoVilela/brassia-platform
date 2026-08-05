package br.com.brew.brassia.traceability.domain;

import java.util.Objects;
import java.util.UUID;

/** Simulado inexistente nesta cervejaria (FDS-004). */
public final class UnknownDrillException extends RuntimeException {

    private final transient UUID id;

    public UnknownDrillException(UUID id) {
        super("simulado inexistente");
        this.id = Objects.requireNonNull(id);
    }

    public UUID id() {
        return id;
    }
}
