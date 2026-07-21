package br.com.brew.brassia.brewery.domain;

import java.util.Objects;
import java.util.UUID;

public record BreweryId(UUID value) {
    public BreweryId {
        Objects.requireNonNull(value, "brewery id");
    }

    public static BreweryId newId() {
        return new BreweryId(UUID.randomUUID());
    }
}
