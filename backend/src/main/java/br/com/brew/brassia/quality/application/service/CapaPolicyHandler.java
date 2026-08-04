package br.com.brew.brassia.quality.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.quality.application.port.inbound.CapaPolicyUseCase;
import br.com.brew.brassia.quality.application.port.outbound.CapaPolicyRepository;
import br.com.brew.brassia.quality.domain.CapaPolicy;
import br.com.brew.brassia.quality.domain.Severity;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class CapaPolicyHandler implements CapaPolicyUseCase {

    private final CapaPolicyRepository policies;
    private final AuditTrail audit;

    public CapaPolicyHandler(CapaPolicyRepository policies, AuditTrail audit) {
        this.policies = Objects.requireNonNull(policies);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public CapaPolicy get(UUID breweryId) {
        return policies.find(breweryId);
    }

    @Override
    public CapaPolicy replace(UUID actorId, UUID breweryId, Map<String, Deadlines> bySeverity) {
        var policy = CapaPolicy.none(breweryId);
        bySeverity.forEach((severity, d) -> policy.set(Severity.valueOf(severity),
                new CapaPolicy.Deadlines(d.containmentDays(), d.investigationDays(),
                        d.verificationDays())));
        policies.save(policy);

        audit.record(AuditEvent.success(breweryId, actorId, "quality.policy.update",
                "quality.capa_policy", breweryId.toString(),
                Map.of("severities", String.valueOf(policy.bySeverity().size()))));
        return policy;
    }
}
