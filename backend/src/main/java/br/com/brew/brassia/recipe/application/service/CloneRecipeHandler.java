package br.com.brew.brassia.recipe.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.recipe.application.port.inbound.CloneRecipeUseCase;
import br.com.brew.brassia.recipe.application.port.outbound.RecipeRepository;
import br.com.brew.brassia.recipe.domain.RecipeName;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class CloneRecipeHandler implements CloneRecipeUseCase {
    private final RecipeRepository repository;
    private final AuditTrail audit;

    public CloneRecipeHandler(RecipeRepository repository, AuditTrail audit) {
        this.repository = Objects.requireNonNull(repository);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var source = repository.findById(command.breweryId(), command.recipeId())
                .orElseThrow(() -> new IllegalArgumentException("receita inexistente"));
        var clone = source.cloneAs(new RecipeName(command.name()));
        if (repository.existsByName(command.breweryId(), clone.name().value().toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("já existe receita com esse nome nesta cervejaria");
        }
        repository.insert(clone);
        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "recipe.clone", "recipe",
                clone.id().value().toString(), Map.of("from", source.id().value().toString())));
        return new Result(clone.id().value(), clone.name().value(), clone.status().name(), clone.version());
    }
}
