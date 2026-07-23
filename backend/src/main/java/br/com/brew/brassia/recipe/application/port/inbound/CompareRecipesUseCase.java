package br.com.brew.brassia.recipe.application.port.inbound;

import java.util.List;
import java.util.UUID;

@FunctionalInterface
public interface CompareRecipesUseCase {
    Result handle(Query query);

    record Query(UUID breweryId, UUID recipeId, UUID otherRecipeId) {}

    record Difference(String field, String left, String right) {}

    record Result(UUID leftId, UUID rightId, List<Difference> differences) {}
}
