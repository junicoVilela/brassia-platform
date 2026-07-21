package br.com.brew.brassia.recipe.application.service;

import br.com.brew.brassia.recipe.application.port.inbound.CreateRecipeUseCase;
import br.com.brew.brassia.recipe.application.port.outbound.RecipeRepository;
import br.com.brew.brassia.recipe.domain.Recipe;
import java.util.Locale;
import java.util.Objects;

public final class CreateRecipeHandler implements CreateRecipeUseCase {
    private final RecipeRepository repository;

    public CreateRecipeHandler(RecipeRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public Result handle(Command command) {
        var recipe = Recipe.draft(command.breweryId(), command.name());
        var normalizedName = recipe.name().value().toLowerCase(Locale.ROOT);
        if (repository.existsByName(command.breweryId(), normalizedName)) {
            throw new IllegalStateException("recipe name already exists");
        }
        repository.save(recipe);
        return new Result(recipe.id().value(), recipe.name().value(), recipe.status().name());
    }
}
