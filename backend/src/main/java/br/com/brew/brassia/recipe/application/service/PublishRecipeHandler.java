package br.com.brew.brassia.recipe.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.recipe.RecipePublished;
import br.com.brew.brassia.recipe.application.port.inbound.PublishRecipeUseCase;
import br.com.brew.brassia.recipe.application.port.outbound.RecipeEventPublisher;
import br.com.brew.brassia.recipe.application.port.outbound.RecipeRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public final class PublishRecipeHandler implements PublishRecipeUseCase {
    private final RecipeRepository repository;
    private final RecipeEventPublisher events;
    private final AuditTrail audit;

    public PublishRecipeHandler(RecipeRepository repository, RecipeEventPublisher events, AuditTrail audit) {
        this.repository = Objects.requireNonNull(repository);
        this.events = Objects.requireNonNull(events);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var recipe = repository.findById(command.breweryId(), command.recipeId())
                .orElseThrow(() -> new IllegalArgumentException("receita inexistente"));
        recipe.publish(); // valida rascunho → publicada (IllegalStateException = 409)
        if (!repository.markPublished(command.breweryId(), command.recipeId())) {
            throw new IllegalStateException("receita não está em rascunho");
        }

        events.publish(new RecipePublished(command.breweryId(), recipe.id().value(),
                (int) recipe.version(), Instant.now()));
        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "recipe.publish", "recipe",
                recipe.id().value().toString(), Map.of("version", Long.toString(recipe.version()))));

        return new Result(recipe.id().value(), recipe.name().value(), recipe.status().name(), recipe.version());
    }
}
