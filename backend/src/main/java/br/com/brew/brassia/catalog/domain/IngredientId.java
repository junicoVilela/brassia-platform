package br.com.brew.brassia.catalog.domain;

import java.util.Objects;
import java.util.UUID;

public record IngredientId(UUID value) {
    public IngredientId {
        Objects.requireNonNull(value, "id");
    }

    public static IngredientId newId() {
        return new IngredientId(UUID.randomUUID());
    }
}
