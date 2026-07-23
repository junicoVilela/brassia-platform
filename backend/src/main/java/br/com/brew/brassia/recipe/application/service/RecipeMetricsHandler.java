package br.com.brew.brassia.recipe.application.service;

import br.com.brew.brassia.recipe.application.port.inbound.RecipeMetricsUseCase;
import br.com.brew.brassia.recipe.application.port.outbound.RecipeMetricsRepository;
import br.com.brew.brassia.recipe.domain.RecipeMetrics;
import java.util.Objects;

public final class RecipeMetricsHandler implements RecipeMetricsUseCase {
    private final RecipeMetricsRepository repository;

    public RecipeMetricsHandler(RecipeMetricsRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public RecipeMetrics handle(Query query) {
        return repository.findByRecipe(query.breweryId(), query.recipeId())
                .orElseThrow(() -> new IllegalArgumentException("metas não calculadas para esta receita"));
    }
}
