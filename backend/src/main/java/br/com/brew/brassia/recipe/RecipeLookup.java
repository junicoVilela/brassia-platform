package br.com.brew.brassia.recipe;

import java.util.Optional;
import java.util.UUID;

public interface RecipeLookup {
    Optional<PublishedRecipe> findPublished(UUID breweryId, UUID recipeId);

    record PublishedRecipe(UUID id, int version, String name) {}
}
