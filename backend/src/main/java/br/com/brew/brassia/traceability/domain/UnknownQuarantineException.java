package br.com.brew.brassia.traceability.domain;

import java.util.Objects;
import java.util.UUID;

/** Quarentena inexistente nesta cervejaria (FDS-002). */
public final class UnknownQuarantineException extends RuntimeException {

    private final transient UUID id;

    public UnknownQuarantineException(UUID id) {
        super("quarentena inexistente");
        this.id = Objects.requireNonNull(id);
    }

    public UUID id() {
        return id;
    }
}
