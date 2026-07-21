package br.com.brew.brassia.recipe.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.recipe.application.port.inbound.CreateRecipeUseCase;
import br.com.brew.brassia.recipe.application.port.outbound.RecipeRepository;
import br.com.brew.brassia.recipe.domain.Recipe;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class CreateRecipeHandler implements CreateRecipeUseCase {
    private final RecipeRepository repository;
    private final AuditTrail audit;

    public CreateRecipeHandler(RecipeRepository repository, AuditTrail audit) {
        this.repository = Objects.requireNonNull(repository);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var recipe = Recipe.draft(command.breweryId(), command.name());
        var normalizedName = recipe.name().value().toLowerCase(Locale.ROOT);
        if (repository.existsByName(command.breweryId(), normalizedName)) {
            throw new IllegalStateException("recipe name already exists");
        }
        repository.save(recipe);
        audit.record(AuditEvent.success(
                command.breweryId(),
                null,
                "recipe.create",
                "recipe",
                recipe.id().value().toString(),
                Map.of("name", recipe.name().value())));
        return new Result(recipe.id().value(), recipe.name().value(), recipe.status().name());
    }
}
