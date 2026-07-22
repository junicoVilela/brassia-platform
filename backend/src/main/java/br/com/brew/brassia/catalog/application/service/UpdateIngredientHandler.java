package br.com.brew.brassia.catalog.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.catalog.application.port.inbound.UpdateIngredientUseCase;
import br.com.brew.brassia.catalog.application.port.outbound.IngredientRepository;
import br.com.brew.brassia.catalog.domain.IngredientName;
import br.com.brew.brassia.catalog.domain.MeasurementUnit;
import java.util.Map;
import java.util.Objects;

public final class UpdateIngredientHandler implements UpdateIngredientUseCase {
    private final IngredientRepository repository;
    private final AuditTrail audit;

    public UpdateIngredientHandler(IngredientRepository repository, AuditTrail audit) {
        this.repository = Objects.requireNonNull(repository);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var ingredient = repository.findById(command.breweryId(), command.ingredientId())
                .orElseThrow(() -> new IllegalArgumentException("ingrediente inexistente"));

        ingredient.update(new IngredientName(command.name()), MeasurementUnit.of(command.useUnit()),
                MeasurementUnit.of(command.purchaseUnit()), command.attributes());

        if (!repository.update(ingredient, command.version())) {
            throw new IllegalStateException("versão do ingrediente divergiu");
        }

        var refreshed = repository.findById(command.breweryId(), command.ingredientId()).orElseThrow();
        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "catalog.ingredient.update",
                "catalog_ingredient", refreshed.id().value().toString(),
                Map.of("type", refreshed.type().name(), "code", refreshed.code().value())));

        return new Result(refreshed.id().value(), refreshed.type().name(), refreshed.code().value(),
                refreshed.name().value(), refreshed.useUnit().name(), refreshed.purchaseUnit().name(),
                refreshed.attributes(), refreshed.active(), refreshed.version());
    }
}
