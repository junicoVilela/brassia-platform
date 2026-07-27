package br.com.brew.brassia.production.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.production.application.port.inbound.ConfirmAlertUseCase;
import br.com.brew.brassia.production.application.port.outbound.AlertRepository;
import br.com.brew.brassia.production.domain.BatchAlert;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Confirma um alerta/ação (PRD-006) de forma idempotente: se já confirmado, é
 * no-op (sem novo efeito). A confirmação é auditada e não avança nenhuma etapa.
 */
public final class ConfirmAlertHandler implements ConfirmAlertUseCase {

    private final AlertRepository alerts;
    private final AuditTrail audit;

    public ConfirmAlertHandler(AlertRepository alerts, AuditTrail audit) {
        this.alerts = Objects.requireNonNull(alerts);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public BatchAlert handle(Command command) {
        var alert = alerts.findById(command.breweryId(), command.alertId())
                .orElseThrow(() -> new IllegalArgumentException("alerta inexistente"));
        if (!alert.batchId().equals(command.batchId())) {
            throw new IllegalArgumentException("alerta não pertence ao lote");
        }
        if (alert.confirmed()) {
            return alert; // idempotente: já confirmado, sem novo efeito
        }

        if (alerts.markConfirmed(command.breweryId(), command.alertId(), Instant.now(), command.actorId())) {
            audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "production.alert.confirm",
                    "production.batch", alert.batchId().toString(), Map.of("alertId", command.alertId().toString())));
        }
        return alerts.findById(command.breweryId(), command.alertId()).orElseThrow();
    }
}
