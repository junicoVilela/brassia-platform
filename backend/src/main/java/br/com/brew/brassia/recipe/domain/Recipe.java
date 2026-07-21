package br.com.brew.brassia.recipe.domain;

import java.util.Objects;
import java.util.UUID;

public final class Recipe {
    private final RecipeId id;
    private final UUID breweryId;
    private RecipeName name;
    private RecipeStatus status;
    private long version;

    private Recipe(RecipeId id, UUID breweryId, RecipeName name) {
        this.id = Objects.requireNonNull(id);
        this.breweryId = Objects.requireNonNull(breweryId);
        this.name = Objects.requireNonNull(name);
        this.status = RecipeStatus.DRAFT;
    }

    public static Recipe draft(UUID breweryId, String name) {
        return new Recipe(RecipeId.newId(), breweryId, new RecipeName(name));
    }

    public RecipeId id() { return id; }
    public UUID breweryId() { return breweryId; }
    public RecipeName name() { return name; }
    public RecipeStatus status() { return status; }
    public long version() { return version; }
}
