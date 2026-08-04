package br.com.brew.brassia.gas.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.gas.application.port.inbound.GasPolicyUseCase;
import br.com.brew.brassia.gas.application.port.outbound.GasPolicyRepository;
import br.com.brew.brassia.gas.domain.GasPolicy;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class GasPolicyHandler implements GasPolicyUseCase {

    private final GasPolicyRepository policies;
    private final AuditTrail audit;

    public GasPolicyHandler(GasPolicyRepository policies, AuditTrail audit) {
        this.policies = Objects.requireNonNull(policies);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public GasPolicy get(UUID breweryId) {
        return policies.find(breweryId);
    }

    @Override
    public GasPolicy update(UUID actorId, UUID breweryId, Integer months) {
        var policy = policies.find(breweryId);
        policy.setRequalificationMonths(months);
        policies.save(policy);

        audit.record(AuditEvent.success(breweryId, actorId, "gas.policy.update", "gas.policy",
                breweryId.toString(), Map.of("requalificationMonths", String.valueOf(months))));
        return policy;
    }
}
