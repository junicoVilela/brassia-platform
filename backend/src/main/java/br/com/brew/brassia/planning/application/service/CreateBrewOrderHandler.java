package br.com.brew.brassia.planning.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.equipment.EquipmentProfileLookup;
import br.com.brew.brassia.planning.application.port.inbound.CreateBrewOrderUseCase;
import br.com.brew.brassia.planning.application.port.outbound.BrewOrderRepository;
import br.com.brew.brassia.planning.domain.BrewOrder;
import br.com.brew.brassia.planning.domain.OrderSnapshot;
import br.com.brew.brassia.planning.domain.SnapshotIncompleteException;
import br.com.brew.brassia.recipe.RecipeLookup;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Objects;

/**
 * Cria uma ordem de produção (BOP-001): só de receita publicada, congelando um
 * snapshot (cálculo da receita + perfil do equipamento) e gerando um código único
 * {@code OP-<ano>-<n>}. Falha com "snapshot incompleto" (409) se a receita ainda
 * não tem métricas calculadas. Registra auditoria; não emite evento (o evento é
 * no release — BOP-002).
 */
public final class CreateBrewOrderHandler implements CreateBrewOrderUseCase {

    private final BrewOrderRepository repository;
    private final RecipeLookup recipes;
    private final EquipmentProfileLookup equipment;
    private final AuditTrail audit;

    public CreateBrewOrderHandler(BrewOrderRepository repository, RecipeLookup recipes,
            EquipmentProfileLookup equipment, AuditTrail audit) {
        this.repository = Objects.requireNonNull(repository);
        this.recipes = Objects.requireNonNull(recipes);
        this.equipment = Objects.requireNonNull(equipment);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var recipe = recipes.findPublishedForOrder(command.breweryId(), command.recipeId())
                .orElseThrow(() -> new IllegalArgumentException("receita não publicada ou inexistente"));

        var metrics = recipe.metrics()
                .orElseThrow(() -> new SnapshotIncompleteException(
                        "snapshot incompleto: métricas da receita não calculadas"));

        var profile = equipment.find(command.breweryId(), recipe.equipmentId())
                .orElseThrow(() -> new IllegalArgumentException("equipamento inexistente"));

        var snapshot = new OrderSnapshot(
                new OrderSnapshot.Recipe(recipe.id(), recipe.version(), recipe.name(), metrics.ogSg(),
                        metrics.fgSg(), metrics.abv(), metrics.ibu(), metrics.colorEbc()),
                new OrderSnapshot.Equipment(recipe.equipmentId(), profile.capacityLiters(),
                        profile.deadSpaceLiters(), profile.mashEfficiencyPercent(), profile.boilOffLitersPerHour()));

        int year = ZonedDateTime.now(ZoneOffset.UTC).getYear();
        long sequence = repository.nextSequence(command.breweryId(), year);
        var code = "OP-%d-%04d".formatted(year, sequence);

        var order = BrewOrder.create(command.breweryId(), code, recipe.id(), recipe.version(),
                command.volumeLiters(), snapshot);
        repository.insert(order);

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "planning.order.create",
                "planning.order", order.id().value().toString(),
                Map.of("code", order.code(), "recipeId", recipe.id().toString())));

        return new Result(order.id().value(), order.code(), order.status().name());
    }
}
