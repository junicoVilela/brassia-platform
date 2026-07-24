package br.com.brew.brassia.referencedata.domain;

import java.util.Objects;
import java.util.UUID;

public record StyleId(UUID value) {
    public StyleId {
        Objects.requireNonNull(value, "value is required");
    }

    public static StyleId newId() {
        return new StyleId(UUID.randomUUID());
    }
}
