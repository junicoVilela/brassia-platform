package br.com.brew.brassia.production.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.calculator.CalculatorEngine;
import br.com.brew.brassia.production.CorrectionApplied;
import br.com.brew.brassia.production.application.port.inbound.ApplyCorrectionUseCase;
import br.com.brew.brassia.production.application.port.outbound.AppliedCorrectionRepository;
import br.com.brew.brassia.production.application.port.outbound.BatchRepository;
import br.com.brew.brassia.production.application.port.outbound.MeasurementRepository;
import br.com.brew.brassia.production.application.port.outbound.ProductionEventPublisher;
import br.com.brew.brassia.production.domain.AppliedCorrection;
import br.com.brew.brassia.production.domain.BatchStatus;
import br.com.brew.brassia.production.domain.BrewCorrections;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Aplica uma correção (CAL-002): valida o lote (em andamento), restringe às
 * correções de brassa, calcula o efeito estimado no motor versionado (planejado) e
 * registra a decisão + evento. Nenhuma ação física; realizado é opcional.
 */
public final class ApplyCorrectionHandler implements ApplyCorrectionUseCase {

    private final BatchRepository batches;
    private final MeasurementRepository measurements;
    private final AppliedCorrectionRepository corrections;
    private final CalculatorEngine engine;
    private final ProductionEventPublisher events;
    private final AuditTrail audit;

    public ApplyCorrectionHandler(BatchRepository batches, MeasurementRepository measurements,
            AppliedCorrectionRepository corrections, CalculatorEngine engine, ProductionEventPublisher events,
            AuditTrail audit) {
        this.batches = Objects.requireNonNull(batches);
        this.measurements = Objects.requireNonNull(measurements);
        this.corrections = Objects.requireNonNull(corrections);
        this.engine = Objects.requireNonNull(engine);
        this.events = Objects.requireNonNull(events);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public AppliedCorrection handle(Command command) {
        var batch = batches.findById(command.breweryId(), command.batchId())
                .orElseThrow(() -> new IllegalArgumentException("lote inexistente"));
        if (batch.status() != BatchStatus.IN_PROGRESS) {
            throw new IllegalStateException("lote não está em andamento");
        }
        if (!BrewCorrections.isCorrection(command.calculator())) {
            throw new IllegalArgumentException("correção de brassa desconhecida: " + command.calculator());
        }
        if (command.sourceMeasurementId() != null
                && !measurements.existsInBatch(command.breweryId(), command.batchId(),
                        command.sourceMeasurementId())) {
            throw new IllegalArgumentException("medição de origem não pertence ao lote");
        }

        // Efeito estimado (planejado) pelo motor versionado — determinístico.
        var computation = engine.compute(command.calculator(), command.inputs());

        var applied = AppliedCorrection.record(command.breweryId(), command.batchId(), command.calculator(),
                command.sourceMeasurementId(), command.note(),
                command.inputs() == null ? Map.of() : command.inputs(), computation.value(), computation.unit(),
                command.realizedValue(), Instant.now(), command.actorId());
        corrections.insert(applied);

        events.publish(new CorrectionApplied(command.breweryId(), command.batchId(), applied.id(),
                applied.calculator(), applied.plannedValue(), applied.plannedUnit(), command.actorId(),
                applied.appliedAt()));
        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "production.correction.apply",
                "production.batch", command.batchId().toString(),
                Map.of("calculator", applied.calculator(),
                        "planned", applied.plannedValue().toPlainString() + " " + applied.plannedUnit())));

        return applied;
    }
}
