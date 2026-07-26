package br.com.brew.brassia.planning.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.equipment.EquipmentCapacityLookup;
import br.com.brew.brassia.planning.application.port.inbound.CreateScheduleEntryUseCase;
import br.com.brew.brassia.planning.application.port.outbound.ScheduleEntryRepository;
import br.com.brew.brassia.planning.domain.ScheduleConflictException;
import br.com.brew.brassia.planning.domain.ScheduleEntry;
import br.com.brew.brassia.planning.domain.ScheduleWindow;
import br.com.brew.brassia.recipe.RecipeLookup;
import java.util.Map;
import java.util.Objects;

/**
 * Cria uma entrada da agenda (PLN-001). Só aceita receita publicada e equipamento
 * existente da cervejaria; bloqueia (409) quando a janela conflita com outra
 * entrada no mesmo equipamento. Registra auditoria; não emite evento de domínio.
 */
public final class CreateScheduleEntryHandler implements CreateScheduleEntryUseCase {

    private final ScheduleEntryRepository repository;
    private final RecipeLookup recipes;
    private final EquipmentCapacityLookup equipment;
    private final AuditTrail audit;

    public CreateScheduleEntryHandler(ScheduleEntryRepository repository, RecipeLookup recipes,
            EquipmentCapacityLookup equipment, AuditTrail audit) {
        this.repository = Objects.requireNonNull(repository);
        this.recipes = Objects.requireNonNull(recipes);
        this.equipment = Objects.requireNonNull(equipment);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        recipes.findPublished(command.breweryId(), command.recipeId())
                .orElseThrow(() -> new IllegalArgumentException("receita não publicada ou inexistente"));

        var capacity = equipment.capacityLiters(command.breweryId(), command.equipmentId())
                .orElseThrow(() -> new IllegalArgumentException("equipamento inexistente"));

        var window = new ScheduleWindow(command.scheduledStart(), command.scheduledEnd());

        var conflicts = repository.findEquipmentConflicts(command.breweryId(), command.equipmentId(),
                window.start(), window.end());
        if (!conflicts.isEmpty()) {
            throw new ScheduleConflictException("conflito de equipamento na janela selecionada");
        }

        var entry = ScheduleEntry.plan(command.breweryId(), command.recipeId(), command.equipmentId(),
                command.assignedUserId(), command.plannedVolumeLiters(), capacity, window);
        repository.insert(entry);

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "planning.schedule.create",
                "planning.schedule", entry.id().value().toString(),
                Map.of("recipeId", command.recipeId().toString(),
                        "equipmentId", command.equipmentId().toString())));

        return new Result(entry.id().value(), entry.status().name());
    }
}
