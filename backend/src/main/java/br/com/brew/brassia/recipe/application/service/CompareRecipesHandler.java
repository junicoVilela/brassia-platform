package br.com.brew.brassia.recipe.application.service;

import br.com.brew.brassia.recipe.application.port.inbound.CompareRecipesUseCase;
import br.com.brew.brassia.recipe.application.port.outbound.RecipeRepository;
import br.com.brew.brassia.recipe.domain.RecipeComparison;
import java.util.Objects;

public final class CompareRecipesHandler implements CompareRecipesUseCase {
    private final RecipeRepository repository;

    public CompareRecipesHandler(RecipeRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public Result handle(Query query) {
        var left = repository.findById(query.breweryId(), query.recipeId())
                .orElseThrow(() -> new IllegalArgumentException("receita inexistente"));
        var right = repository.findById(query.breweryId(), query.otherRecipeId())
                .orElseThrow(() -> new IllegalArgumentException("receita de comparação inexistente"));
        var comparison = RecipeComparison.compare(left, right);
        var differences = comparison.differences().stream()
                .map(d -> new Difference(d.field(), d.left(), d.right()))
                .toList();
        return new Result(left.id().value(), right.id().value(), differences);
    }
}
