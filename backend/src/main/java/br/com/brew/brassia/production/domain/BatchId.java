package br.com.brew.brassia.production.domain;

import java.util.Objects;
import java.util.UUID;

public record BatchId(UUID value) {
    public BatchId {
        Objects.requireNonNull(value, "value is required");
    }

    public static BatchId newId() {
        return new BatchId(UUID.randomUUID());
    }
}
