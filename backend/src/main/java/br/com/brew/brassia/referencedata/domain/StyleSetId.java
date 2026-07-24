package br.com.brew.brassia.referencedata.domain;

import java.util.Objects;
import java.util.UUID;

public record StyleSetId(UUID value) {
    public StyleSetId {
        Objects.requireNonNull(value, "value is required");
    }

    public static StyleSetId newId() {
        return new StyleSetId(UUID.randomUUID());
    }
}
