package br.com.brew.brassia.production.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.production.application.port.inbound.CreateAlertUseCase;
import br.com.brew.brassia.production.application.port.outbound.AlertRepository;
import br.com.brew.brassia.production.application.port.outbound.BatchRepository;
import br.com.brew.brassia.production.domain.BatchAlert;
import br.com.brew.brassia.production.domain.BatchAlertKind;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Cria um item na central de alertas do lote (PRD-006). Valida o lote (tenant) e
 * audita. Não avança etapa nem toca outros estados.
 */
public final class CreateAlertHandler implements CreateAlertUseCase {

    private final BatchRepository batches;
    private final AlertRepository alerts;
    private final AuditTrail audit;

    public CreateAlertHandler(BatchRepository batches, AlertRepository alerts, AuditTrail audit) {
        this.batches = Objects.requireNonNull(batches);
        this.alerts = Objects.requireNonNull(alerts);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        batches.findById(command.breweryId(), command.batchId())
                .orElseThrow(() -> new IllegalArgumentException("lote inexistente"));

        var alert = BatchAlert.open(command.breweryId(), command.batchId(),
                BatchAlertKind.of(command.kind()), command.message(), command.plannedAt(), command.occurredAt(),
                Instant.now(), command.actorId());
        alerts.insert(alert);

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "production.alert.create",
                "production.batch", command.batchId().toString(), Map.of("kind", alert.kind().name())));

        return new Result(alert.id());
    }
}
