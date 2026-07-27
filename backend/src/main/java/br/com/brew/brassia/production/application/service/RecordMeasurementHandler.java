package br.com.brew.brassia.production.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.production.application.port.inbound.RecordMeasurementUseCase;
import br.com.brew.brassia.production.application.port.outbound.BatchRepository;
import br.com.brew.brassia.production.application.port.outbound.MeasurementRepository;
import br.com.brew.brassia.production.domain.BatchStatus;
import br.com.brew.brassia.production.domain.Measurement;
import br.com.brew.brassia.production.domain.MeasurementKind;
import br.com.brew.brassia.production.domain.MeasurementSource;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Registra uma medição imutável no lote (PRD-003). Só lote em andamento; a
 * etapa (se informada) deve ser do lote; a unidade é validada contra a grandeza
 * no domínio. Append-only + auditoria.
 */
public final class RecordMeasurementHandler implements RecordMeasurementUseCase {

    private final BatchRepository batches;
    private final MeasurementRepository measurements;
    private final AuditTrail audit;

    public RecordMeasurementHandler(BatchRepository batches, MeasurementRepository measurements, AuditTrail audit) {
        this.batches = Objects.requireNonNull(batches);
        this.measurements = Objects.requireNonNull(measurements);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var batch = batches.findById(command.breweryId(), command.batchId())
                .orElseThrow(() -> new IllegalArgumentException("lote inexistente"));
        if (batch.status() != BatchStatus.IN_PROGRESS) {
            throw new IllegalStateException("lote não está em andamento");
        }
        if (command.stepId() != null
                && batch.steps().stream().noneMatch(s -> s.id().equals(command.stepId()))) {
            throw new IllegalArgumentException("etapa não pertence ao lote");
        }

        var measurement = Measurement.record(command.breweryId(), command.batchId(), command.stepId(),
                MeasurementKind.of(command.kind()), command.value(), command.unit(), command.temperatureC(),
                command.method(), MeasurementSource.of(command.source()), Instant.now(), command.actorId());
        measurements.insert(measurement);

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "production.measurement.record",
                "production.batch", command.batchId().toString(),
                Map.of("kind", measurement.kind().name(), "unit", measurement.unit())));

        return new Result(measurement.id());
    }
}
