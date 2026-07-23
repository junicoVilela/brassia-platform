package br.com.brew.brassia.recipe.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.recipe.application.port.inbound.CreateRecipeVersionUseCase;
import br.com.brew.brassia.recipe.application.port.outbound.RecipeRepository;
import java.util.Map;
import java.util.Objects;

public final class CreateRecipeVersionHandler implements CreateRecipeVersionUseCase {
    private final RecipeRepository repository;
    private final AuditTrail audit;

    public CreateRecipeVersionHandler(RecipeRepository repository, AuditTrail audit) {
        this.repository = Objects.requireNonNull(repository);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var published = repository.findById(command.breweryId(), command.recipeId())
                .orElseThrow(() -> new IllegalArgumentException("receita inexistente"));
        var next = published.nextDraftVersion(); // exige publicada (IllegalStateException = 409)
        repository.insert(next);

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "recipe.version.create", "recipe",
                next.id().value().toString(), Map.of("version", Long.toString(next.version()),
                        "previous", published.id().value().toString())));

        return new Result(next.id().value(), next.name().value(), next.status().name(), next.version(),
                next.previousRecipeId());
    }
}
