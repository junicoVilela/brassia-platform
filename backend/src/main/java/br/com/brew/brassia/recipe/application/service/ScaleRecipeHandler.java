package br.com.brew.brassia.recipe.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.equipment.EquipmentCapacityLookup;
import br.com.brew.brassia.recipe.application.port.inbound.ScaleRecipeUseCase;
import br.com.brew.brassia.recipe.application.port.outbound.RecipeRepository;
import br.com.brew.brassia.recipe.domain.RecipeName;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class ScaleRecipeHandler implements ScaleRecipeUseCase {
    private final RecipeRepository repository;
    private final EquipmentCapacityLookup equipment;
    private final AuditTrail audit;

    public ScaleRecipeHandler(RecipeRepository repository, EquipmentCapacityLookup equipment, AuditTrail audit) {
        this.repository = Objects.requireNonNull(repository);
        this.equipment = Objects.requireNonNull(equipment);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var source = repository.findById(command.breweryId(), command.recipeId())
                .orElseThrow(() -> new IllegalArgumentException("receita inexistente"));
        var capacity = equipment.capacityLiters(command.breweryId(), source.equipmentId())
                .orElseThrow(() -> new IllegalArgumentException("equipamento inexistente"));

        var scaled = source.scaleTo(new RecipeName(command.name()), command.batchVolumeLiters(), capacity);
        if (repository.existsByName(command.breweryId(), scaled.name().value().toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("já existe receita com esse nome nesta cervejaria");
        }
        repository.insert(scaled);
        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "recipe.scale", "recipe",
                scaled.id().value().toString(), Map.of("from", source.id().value().toString(),
                        "batchVolumeLiters", scaled.batchVolumeLiters().toPlainString())));
        return new Result(scaled.id().value(), scaled.name().value(), scaled.status().name(), scaled.version(),
                scaled.batchVolumeLiters());
    }
}
