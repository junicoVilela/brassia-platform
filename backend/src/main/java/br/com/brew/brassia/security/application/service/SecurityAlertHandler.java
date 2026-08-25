package br.com.brew.brassia.security.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.security.application.port.inbound.ManageSecurityAlertUseCase;
import br.com.brew.brassia.security.application.port.outbound.SecurityAlertRepository;
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
        // A busca já vem escopada (DEB-INT-003). Antes disto o escopo era uma conferência aqui, feita
        // depois de ler o alerta — e o SQL, que também filtrava, não ligava o parâmetro: a chamada
        // estourava antes de chegar à conferência, e resolver alerta nunca funcionou.
        alerts.findById(breweryId, alertId)
                .orElseThrow(() -> new IllegalArgumentException("alerta inexistente"));
        alerts.updateStatus(breweryId, alertId, status, actorId);
        audit.record(AuditEvent.success(breweryId, actorId, "security.alert.update",
                "security_alert", alertId.toString(), Map.of("status", status)));
    }
}
