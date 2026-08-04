package br.com.brew.brassia.sensory.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.sensory.application.port.inbound.SensoryPolicyUseCase;
import br.com.brew.brassia.sensory.application.port.outbound.SensoryPolicyRepository;
import br.com.brew.brassia.sensory.domain.SensoryPolicy;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class SensoryPolicyHandler implements SensoryPolicyUseCase {

    private final SensoryPolicyRepository policies;
    private final AuditTrail audit;

    public SensoryPolicyHandler(SensoryPolicyRepository policies, AuditTrail audit) {
        this.policies = Objects.requireNonNull(policies);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public SensoryPolicy get(UUID breweryId) {
        return policies.find(breweryId);
    }

    @Override
    public SensoryPolicy update(UUID actorId, UUID breweryId, int maxScore) {
        var policy = policies.find(breweryId);
        policy.setMaxScore(maxScore);
        policies.save(policy);

        audit.record(AuditEvent.success(breweryId, actorId, "sensory.policy.update", "sensory.policy",
                breweryId.toString(), Map.of("maxScore", String.valueOf(maxScore))));
        return policy;
    }
}
