package br.com.brew.brassia.catalog.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.catalog.application.port.inbound.RegisterIngredientUseCase;
import br.com.brew.brassia.catalog.application.port.outbound.IngredientRepository;
import br.com.brew.brassia.catalog.domain.Ingredient;
import br.com.brew.brassia.catalog.domain.IngredientCode;
import br.com.brew.brassia.catalog.domain.IngredientName;
import br.com.brew.brassia.catalog.domain.IngredientType;
import br.com.brew.brassia.catalog.domain.MeasurementUnit;
import java.util.Map;
import java.util.Objects;

public final class RegisterIngredientHandler implements RegisterIngredientUseCase {
    private final IngredientRepository repository;
    private final AuditTrail audit;

    public RegisterIngredientHandler(IngredientRepository repository, AuditTrail audit) {
        this.repository = Objects.requireNonNull(repository);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var type = IngredientType.of(command.type());
        var code = new IngredientCode(command.code());
        var name = new IngredientName(command.name());
        var useUnit = MeasurementUnit.of(command.useUnit());
        var purchaseUnit = MeasurementUnit.of(command.purchaseUnit());

        if (repository.existsByCode(command.breweryId(), code.value())) {
            throw new IllegalStateException("código de ingrediente já existe nesta cervejaria");
        }

        var ingredient = Ingredient.register(
                command.breweryId(), type, code, name, useUnit, purchaseUnit, command.attributes());
        repository.insert(ingredient);

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "catalog.ingredient.create",
                "catalog_ingredient", ingredient.id().value().toString(),
                Map.of("type", type.name(), "code", code.value())));

        return new Result(ingredient.id().value(), type.name(), code.value(), name.value(),
                useUnit.name(), purchaseUnit.name(), ingredient.attributes(), ingredient.active(),
                ingredient.version());
    }
}
