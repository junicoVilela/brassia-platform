package br.com.brew.brassia.purchasing.domain;

import java.util.Objects;
import java.util.UUID;

public record SupplierId(UUID value) {
    public SupplierId {
        Objects.requireNonNull(value, "value is required");
    }

    public static SupplierId newId() {
        return new SupplierId(UUID.randomUUID());
    }
}
