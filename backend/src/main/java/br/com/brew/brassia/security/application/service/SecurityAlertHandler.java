package br.com.brew.brassia.security.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.security.application.port.inbound.ManageSecurityAlertUseCase;
import br.com.brew.brassia.security.application.port.outbound.SecurityAlertRepository;
import br.com.brew.brassia.shared.security.ForbiddenException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class SecurityAlertHandler {
    private final SecurityAlertRepository alerts;
    private final AuditTrail audit;

    public SecurityAlertHandler(SecurityAlertRepository alerts, AuditTrail audit) {
        this.alerts = Objects.requireNonNull(alerts);
        this.audit = Objects.requireNonNull(audit);
    }

    public List<SecurityAlertRepository.AlertView> list(UUID breweryId, String status) {
        return alerts.listByBrewery(breweryId, status, 100);
    }

    public void updateStatus(UUID breweryId, UUID actorId, UUID alertId, String status) {
        var alert = alerts.findById(alertId).orElseThrow(() -> new IllegalArgumentException("alerta inexistente"));
        if (alert.breweryId() != null && !alert.breweryId().equals(breweryId)) {
            throw new ForbiddenException("alerta de outra cervejaria");
        }
        alerts.updateStatus(alertId, status, actorId);
        audit.record(AuditEvent.success(breweryId, actorId, "security.alert.update",
                "security_alert", alertId.toString(), Map.of("status", status)));
    }
}
