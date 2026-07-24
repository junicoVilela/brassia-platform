package br.com.brew.brassia.referencedata.domain;

import java.util.Objects;
import java.util.UUID;

public record ReferenceDatasetId(UUID value) {
    public ReferenceDatasetId {
        Objects.requireNonNull(value, "value is required");
    }

    public static ReferenceDatasetId newId() {
        return new ReferenceDatasetId(UUID.randomUUID());
    }
}
