package br.com.brew.brassia.sanitation.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.sanitation.application.port.inbound.InterruptCycleUseCase;
import br.com.brew.brassia.sanitation.application.port.outbound.CleaningCycleRepository;
import br.com.brew.brassia.sanitation.domain.CleaningCycle;
import java.util.Map;
import java.util.Objects;

/** Interrompe um ciclo em andamento, preservando o estado (CLN-003). */
public final class InterruptCycleHandler implements InterruptCycleUseCase {

    private final CleaningCycleRepository cycles;
    private final AuditTrail audit;

    public InterruptCycleHandler(CleaningCycleRepository cycles, AuditTrail audit) {
        this.cycles = Objects.requireNonNull(cycles);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public void handle(Command command) {
        CleaningCycle cycle = cycles.findForUpdate(command.breweryId(), command.cycleId())
                .orElseThrow(() -> new IllegalArgumentException("ciclo inexistente"));
        cycle.interrupt(command.reason());
        cycles.update(cycle);

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "sanitation.cycle.interrupt",
                "sanitation.cycle", cycle.id().toString(), Map.of()));
    }
}
