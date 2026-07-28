package br.com.brew.brassia.sanitation.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.sanitation.application.port.inbound.RecordStepUseCase;
import br.com.brew.brassia.sanitation.application.port.outbound.CleaningCycleRepository;
import br.com.brew.brassia.sanitation.domain.CleaningCycle;
import br.com.brew.brassia.sanitation.domain.StepExecution;
import java.util.Map;
import java.util.Objects;

/**
 * Registra a execução de uma etapa do ciclo (CLN-003). A validação de faixa (parâmetro
 * fora da ficha) e de ordem ocorre no agregado; aqui apenas travamos a linha, persistimos
 * e auditamos (marcando override quando houve desvio autorizado).
 */
public final class RecordStepHandler implements RecordStepUseCase {

    private final CleaningCycleRepository cycles;
    private final AuditTrail audit;

    public RecordStepHandler(CleaningCycleRepository cycles, AuditTrail audit) {
        this.cycles = Objects.requireNonNull(cycles);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public void handle(Command command) {
        CleaningCycle cycle = cycles.findForUpdate(command.breweryId(), command.cycleId())
                .orElseThrow(() -> new IllegalArgumentException("ciclo inexistente"));
        cycle.recordStep(command.sequence(), new StepExecution(
                command.measuredConcentrationPct(), command.measuredTempC(), command.measuredTimeMinutes(),
                command.flow(), command.evidence(), command.outOfOrderReason(),
                command.override(), command.overrideReason()));
        cycles.update(cycle);

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "sanitation.cycle.record-step",
                "sanitation.cycle", cycle.id().toString(),
                Map.of("sequence", String.valueOf(command.sequence()),
                        "override", String.valueOf(command.override()))));
    }
}
