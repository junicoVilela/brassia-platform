package br.com.brew.brassia.recipe.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.equipment.EquipmentCapacityLookup;
import br.com.brew.brassia.recipe.application.port.inbound.CreateRecipeUseCase;
import br.com.brew.brassia.recipe.application.port.outbound.RecipeRepository;
import br.com.brew.brassia.recipe.domain.Recipe;
import br.com.brew.brassia.recipe.domain.RecipeItem;
import br.com.brew.brassia.recipe.domain.RecipeStage;
import br.com.brew.brassia.recipe.domain.RecipeTargets;
import br.com.brew.brassia.recipe.domain.RecipeUnit;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class CreateRecipeHandler implements CreateRecipeUseCase {
    private final RecipeRepository repository;
    private final EquipmentCapacityLookup equipment;
    private final AuditTrail audit;

    public CreateRecipeHandler(RecipeRepository repository, EquipmentCapacityLookup equipment, AuditTrail audit) {
        this.repository = Objects.requireNonNull(repository);
        this.equipment = Objects.requireNonNull(equipment);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var capacity = equipment.capacityLiters(command.breweryId(), command.equipmentId())
                .orElseThrow(() -> new IllegalArgumentException("equipamento inexistente"));

        var items = command.items() == null ? null : command.items().stream()
                .map(i -> new RecipeItem(i.ingredientId(), RecipeStage.of(i.stage()), i.quantity(),
                        RecipeUnit.of(i.unit()), i.timingMinutes(), i.percentage()))
                .toList();
        var targets = new RecipeTargets(command.targetOgPoints(), command.targetIbu(), command.targetColorEbc(),
                command.targetAbv());

        var recipe = Recipe.draft(command.breweryId(), command.name(), command.equipmentId(),
                command.batchVolumeLiters(), capacity, targets, command.boilTimeMinutes(), items);

        var normalizedName = recipe.name().value().toLowerCase(Locale.ROOT);
        if (repository.existsByName(command.breweryId(), normalizedName)) {
            throw new IllegalStateException("já existe receita com esse nome nesta cervejaria");
        }
        repository.insert(recipe);

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "recipe.create", "recipe",
                recipe.id().value().toString(),
                Map.of("name", recipe.name().value(), "items", Integer.toString(recipe.items().size()))));

        return new Result(recipe.id().value(), recipe.name().value(), recipe.status().name());
    }
}
