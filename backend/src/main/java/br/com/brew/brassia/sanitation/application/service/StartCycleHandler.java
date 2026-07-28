package br.com.brew.brassia.sanitation.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.equipment.EquipmentProfileLookup;
import br.com.brew.brassia.sanitation.application.port.inbound.StartCycleUseCase;
import br.com.brew.brassia.sanitation.application.port.outbound.CleaningCycleRepository;
import br.com.brew.brassia.sanitation.application.port.outbound.ProcedureRepository;
import br.com.brew.brassia.sanitation.domain.CleaningCycle;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Inicia um ciclo (CLN-003): exige POP publicado (snapshot das faixas) e um equipamento
 * existente da mesma cervejaria (validado via módulo equipment). Audita.
 */
public final class StartCycleHandler implements StartCycleUseCase {

    private final CleaningCycleRepository cycles;
    private final ProcedureRepository procedures;
    private final EquipmentProfileLookup equipment;
    private final AuditTrail audit;

    public StartCycleHandler(CleaningCycleRepository cycles, ProcedureRepository procedures,
            EquipmentProfileLookup equipment, AuditTrail audit) {
        this.cycles = Objects.requireNonNull(cycles);
        this.procedures = Objects.requireNonNull(procedures);
        this.equipment = Objects.requireNonNull(equipment);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public UUID handle(Command command) {
        if (command.procedureCode() == null || command.procedureCode().isBlank()) {
            throw new IllegalArgumentException("código do POP é obrigatório");
        }
        if (command.equipmentId() == null) {
            throw new IllegalArgumentException("equipamento é obrigatório");
        }
        var procedure = procedures.findLatestPublishedByCode(command.breweryId(), command.procedureCode().trim())
                .orElseThrow(() -> new IllegalArgumentException(
                        "não há POP publicado para o código: " + command.procedureCode()));
        if (equipment.find(command.breweryId(), command.equipmentId()).isEmpty()) {
            throw new IllegalArgumentException("equipamento não encontrado: " + command.equipmentId());
        }

        var cycle = CleaningCycle.start(command.breweryId(), procedure, command.equipmentId());
        cycles.insert(cycle);

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "sanitation.cycle.start",
                "sanitation.cycle", cycle.id().toString(),
                Map.of("procedureCode", procedure.code(), "procedureVersion", String.valueOf(procedure.version()),
                        "equipmentId", command.equipmentId().toString())));
        return cycle.id();
    }
}
