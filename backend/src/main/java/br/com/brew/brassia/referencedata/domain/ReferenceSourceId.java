package br.com.brew.brassia.referencedata.domain;

import java.util.Objects;
import java.util.UUID;

public record ReferenceSourceId(UUID value) {
    public ReferenceSourceId {
        Objects.requireNonNull(value, "value is required");
    }

    public static ReferenceSourceId newId() {
        return new ReferenceSourceId(UUID.randomUUID());
    }
}
