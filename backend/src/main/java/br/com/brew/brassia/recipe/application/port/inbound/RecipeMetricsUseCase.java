package br.com.brew.brassia.recipe.application.port.inbound;

import br.com.brew.brassia.recipe.domain.RecipeMetrics;
import java.util.UUID;

@FunctionalInterface
public interface RecipeMetricsUseCase {
    RecipeMetrics handle(Query query);

    record Query(UUID breweryId, UUID recipeId) {}
}
