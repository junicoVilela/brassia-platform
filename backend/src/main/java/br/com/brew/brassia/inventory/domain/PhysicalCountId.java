package br.com.brew.brassia.inventory.domain;

import java.util.Objects;
import java.util.UUID;

public record PhysicalCountId(UUID value) {
    public PhysicalCountId {
        Objects.requireNonNull(value, "value is required");
    }

    public static PhysicalCountId newId() {
        return new PhysicalCountId(UUID.randomUUID());
    }
}
