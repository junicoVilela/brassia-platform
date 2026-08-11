package br.com.brew.brassia.sanitation.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.equipment.EquipmentCleanlinessCommands;
import br.com.brew.brassia.sanitation.CleaningCycleReleased;
import br.com.brew.brassia.sanitation.application.port.inbound.ReleaseCycleUseCase;
import br.com.brew.brassia.sanitation.application.port.outbound.CleaningCycleEventPublisher;
import br.com.brew.brassia.sanitation.application.port.outbound.CleaningCycleRepository;
import br.com.brew.brassia.sanitation.domain.CleaningCycle;
import java.util.Map;
import java.util.Objects;

/**
 * Libera um ciclo verificado (CLN-004). Exige verificação aprovada — não passa com
 * limpeza reprovada. Audita e publica {@link CleaningCycleReleased}.
 */
public final class ReleaseCycleHandler implements ReleaseCycleUseCase {

    private final CleaningCycleRepository cycles;
    private final CleaningCycleEventPublisher events;
    private final EquipmentCleanlinessCommands equipment;
    private final AuditTrail audit;

    public ReleaseCycleHandler(CleaningCycleRepository cycles, CleaningCycleEventPublisher events,
            EquipmentCleanlinessCommands equipment, AuditTrail audit) {
        this.cycles = Objects.requireNonNull(cycles);
        this.events = Objects.requireNonNull(events);
        this.equipment = Objects.requireNonNull(equipment);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public void handle(Command command) {
        CleaningCycle cycle = cycles.findForUpdate(command.breweryId(), command.cycleId())
                .orElseThrow(() -> new IllegalArgumentException("ciclo inexistente"));
        cycle.release();
        cycles.update(cycle);

        // CLN-004-A: o equipamento fica limpo aqui, dentro da mesma transação. O evento continua sendo
        // publicado para quem quiser reagir, mas o estado não depende de ninguém ter escutado — um efeito
        // que a plataforma promete não pode ficar refém de um listener que alguém esqueceu de registrar.
        equipment.markCleanedByCycle(cycle.breweryId(), cycle.equipmentId(), cycle.id(), command.actorId(),
                cycle.decidedAt());

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "sanitation.cycle.release",
                "sanitation.cycle", cycle.id().toString(), Map.of("equipmentId", cycle.equipmentId().toString())));
        events.publish(new CleaningCycleReleased(cycle.breweryId(), cycle.id(), cycle.equipmentId(),
                cycle.procedureCode(), cycle.procedureVersion(), command.actorId(), cycle.decidedAt()));
    }
}
