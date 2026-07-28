package br.com.brew.brassia.sanitation.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.sanitation.application.port.inbound.ResumeCycleUseCase;
import br.com.brew.brassia.sanitation.application.port.outbound.CleaningCycleRepository;
import br.com.brew.brassia.sanitation.domain.CleaningCycle;
import java.util.Map;
import java.util.Objects;

/** Retoma um ciclo interrompido (CLN-003). */
public final class ResumeCycleHandler implements ResumeCycleUseCase {

    private final CleaningCycleRepository cycles;
    private final AuditTrail audit;

    public ResumeCycleHandler(CleaningCycleRepository cycles, AuditTrail audit) {
        this.cycles = Objects.requireNonNull(cycles);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public void handle(Command command) {
        CleaningCycle cycle = cycles.findForUpdate(command.breweryId(), command.cycleId())
                .orElseThrow(() -> new IllegalArgumentException("ciclo inexistente"));
        cycle.resume();
        cycles.update(cycle);

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "sanitation.cycle.resume",
                "sanitation.cycle", cycle.id().toString(), Map.of()));
    }
}
