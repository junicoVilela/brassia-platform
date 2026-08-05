package br.com.brew.brassia.traceability.domain;

import java.util.Objects;
import java.util.UUID;

/** Recall inexistente nesta cervejaria (FDS-003). */
public final class UnknownRecallException extends RuntimeException {

    private final transient UUID id;

    public UnknownRecallException(UUID id) {
        super("recall inexistente");
        this.id = Objects.requireNonNull(id);
    }

    public UUID id() {
        return id;
    }
}
