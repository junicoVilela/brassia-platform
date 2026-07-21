package br.com.brew.brassia.recipe.domain;

import java.util.Objects;
import java.util.UUID;

public record RecipeId(UUID value) {
    public RecipeId {
        Objects.requireNonNull(value, "value is required");
    }

    public static RecipeId newId() {
        return new RecipeId(UUID.randomUUID());
    }
}
