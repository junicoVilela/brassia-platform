package br.com.brew.brassia.planning.application.service;

import br.com.brew.brassia.equipment.EquipmentCapacityLookup;
import br.com.brew.brassia.planning.application.port.inbound.SimulateScheduleUseCase;
import br.com.brew.brassia.planning.application.port.outbound.ScheduleEntryRepository;
import br.com.brew.brassia.planning.domain.ScheduleWindow;
import java.util.List;
import java.util.Objects;

/**
 * Simula o agendamento: valida equipamento e janela e lista conflitos, sem
 * persistir nada (PLN-001 — "simulação não altera estado").
 */
public final class SimulateScheduleHandler implements SimulateScheduleUseCase {

    private final ScheduleEntryRepository repository;
    private final EquipmentCapacityLookup equipment;

    public SimulateScheduleHandler(ScheduleEntryRepository repository, EquipmentCapacityLookup equipment) {
        this.repository = Objects.requireNonNull(repository);
        this.equipment = Objects.requireNonNull(equipment);
    }

    @Override
    public Result handle(Query query) {
        equipment.capacityLiters(query.breweryId(), query.equipmentId())
                .orElseThrow(() -> new IllegalArgumentException("equipamento inexistente"));

        var window = new ScheduleWindow(query.scheduledStart(), query.scheduledEnd());

        List<Conflict> conflicts = repository
                .findEquipmentConflicts(query.breweryId(), query.equipmentId(), window.start(), window.end())
                .stream()
                .map(c -> new Conflict(c.entryId(), c.start(), c.end()))
                .toList();

        return new Result(!conflicts.isEmpty(), conflicts);
    }
}
