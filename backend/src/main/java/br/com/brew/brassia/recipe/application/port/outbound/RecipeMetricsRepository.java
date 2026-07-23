package br.com.brew.brassia.recipe.application.port.outbound;

import br.com.brew.brassia.recipe.domain.RecipeMetrics;
import java.util.Optional;
import java.util.UUID;

public interface RecipeMetricsRepository {
    /** Grava (ou substitui) as metas atuais da receita. */
    void upsert(RecipeMetrics metrics);

    Optional<RecipeMetrics> findByRecipe(UUID breweryId, UUID recipeId);
}
