package br.com.brew.brassia.planning.domain;

import java.util.Objects;
import java.util.UUID;

public record BrewOrderId(UUID value) {
    public BrewOrderId {
        Objects.requireNonNull(value, "value is required");
    }

    public static BrewOrderId newId() {
        return new BrewOrderId(UUID.randomUUID());
    }
}
