package br.com.brew.brassia.sanitation.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.sanitation.application.port.inbound.RecordVerificationUseCase;
import br.com.brew.brassia.sanitation.application.port.outbound.CleaningCycleRepository;
import br.com.brew.brassia.sanitation.domain.CleaningCycle;
import java.util.Map;
import java.util.Objects;

/** Registra a verificação (enxágue/visual/ATP/micro) de um ciclo concluído (CLN-004). */
public final class RecordVerificationHandler implements RecordVerificationUseCase {

    private final CleaningCycleRepository cycles;
    private final AuditTrail audit;

    public RecordVerificationHandler(CleaningCycleRepository cycles, AuditTrail audit) {
        this.cycles = Objects.requireNonNull(cycles);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public void handle(Command command) {
        CleaningCycle cycle = cycles.findForUpdate(command.breweryId(), command.cycleId())
                .orElseThrow(() -> new IllegalArgumentException("ciclo inexistente"));
        cycle.recordVerification(command.rinseOk(), command.visualOk(), command.atpRlu(), command.atpThreshold(),
                command.microOk());
        cycles.update(cycle);

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "sanitation.cycle.verify",
                "sanitation.cycle", cycle.id().toString(),
                Map.of("passed", String.valueOf(cycle.verification().passed()))));
    }
}
