package br.com.brew.brassia.catalog.domain;

import java.util.Objects;
import java.util.UUID;

public record TechnicalProfileId(UUID value) {
    public TechnicalProfileId {
        Objects.requireNonNull(value, "value is required");
    }

    public static TechnicalProfileId newId() {
        return new TechnicalProfileId(UUID.randomUUID());
    }
}
