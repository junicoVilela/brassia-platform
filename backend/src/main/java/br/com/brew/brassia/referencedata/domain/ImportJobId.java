package br.com.brew.brassia.referencedata.domain;

import java.util.Objects;
import java.util.UUID;

public record ImportJobId(UUID value) {
    public ImportJobId {
        Objects.requireNonNull(value, "value is required");
    }

    public static ImportJobId newId() {
        return new ImportJobId(UUID.randomUUID());
    }
}
