package br.com.brew.brassia.sanitation.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.sanitation.application.port.inbound.RecordConsumptionUseCase;
import br.com.brew.brassia.sanitation.application.port.outbound.CleaningCycleRepository;
import br.com.brew.brassia.sanitation.domain.CleaningCycle;
import java.util.Map;
import java.util.Objects;

/** Registra/atualiza o consumo (água/energia/produto) de um ciclo encerrado (CLN-005). */
public final class RecordConsumptionHandler implements RecordConsumptionUseCase {

    private final CleaningCycleRepository cycles;
    private final AuditTrail audit;

    public RecordConsumptionHandler(CleaningCycleRepository cycles, AuditTrail audit) {
        this.cycles = Objects.requireNonNull(cycles);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public void handle(Command command) {
        CleaningCycle cycle = cycles.findForUpdate(command.breweryId(), command.cycleId())
                .orElseThrow(() -> new IllegalArgumentException("ciclo inexistente"));
        cycle.recordConsumption(command.waterLiters(), command.energyKwh(), command.productKg());
        cycles.update(cycle);

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "sanitation.consumption.record",
                "sanitation.cycle", cycle.id().toString(),
                Map.of("waterLiters", String.valueOf(command.waterLiters()),
                        "energyKwh", String.valueOf(command.energyKwh()))));
    }
}
