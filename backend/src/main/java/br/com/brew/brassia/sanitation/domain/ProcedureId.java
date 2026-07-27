package br.com.brew.brassia.sanitation.domain;

import java.util.Objects;
import java.util.UUID;

public record ProcedureId(UUID value) {
    public ProcedureId {
        Objects.requireNonNull(value, "value is required");
    }

    public static ProcedureId newId() {
        return new ProcedureId(UUID.randomUUID());
    }
}
