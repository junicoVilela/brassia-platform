package br.com.brew.brassia.recipe.application.service;

import br.com.brew.brassia.recipe.application.port.inbound.GetRecipeUseCase;
import br.com.brew.brassia.recipe.application.port.outbound.RecipeRepository;
import br.com.brew.brassia.recipe.domain.Recipe;
import br.com.brew.brassia.recipe.domain.RecipeTargets;
import java.util.Objects;

public final class GetRecipeHandler implements GetRecipeUseCase {
    private final RecipeRepository repository;

    public GetRecipeHandler(RecipeRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public Result handle(Query query) {
        var recipe = repository.findById(query.breweryId(), query.recipeId())
                .orElseThrow(() -> new IllegalArgumentException("receita inexistente"));
        var items = recipe.items().stream()
                .map(i -> new Item(i.ingredientId(), i.stage().name(), i.quantity(), i.unit().name(),
                        i.timingMinutes(), i.percentage()))
                .toList();
        RecipeTargets t = recipe.targets();
        return new Result(recipe.id().value(), recipe.name().value(), recipe.status().name(),
                recipe.equipmentId(), recipe.batchVolumeLiters(), t.ogPoints(), t.ibu(), t.colorEbc(), t.abv(),
                recipe.boilTimeMinutes(), items, recipe.version());
    }
}
