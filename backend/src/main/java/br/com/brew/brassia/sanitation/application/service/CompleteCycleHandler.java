package br.com.brew.brassia.sanitation.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.sanitation.application.port.inbound.CompleteCycleUseCase;
import br.com.brew.brassia.sanitation.application.port.outbound.CleaningCycleRepository;
import br.com.brew.brassia.sanitation.domain.CleaningCycle;
import java.util.Map;
import java.util.Objects;

/** Encerra a execução do ciclo; exige todas as etapas concluídas (CLN-003). */
public final class CompleteCycleHandler implements CompleteCycleUseCase {

    private final CleaningCycleRepository cycles;
    private final AuditTrail audit;

    public CompleteCycleHandler(CleaningCycleRepository cycles, AuditTrail audit) {
        this.cycles = Objects.requireNonNull(cycles);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public void handle(Command command) {
        CleaningCycle cycle = cycles.findForUpdate(command.breweryId(), command.cycleId())
                .orElseThrow(() -> new IllegalArgumentException("ciclo inexistente"));
        cycle.complete();
        cycles.update(cycle);

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "sanitation.cycle.complete",
                "sanitation.cycle", cycle.id().toString(), Map.of()));
    }
}
