package br.com.brew.brassia.sanitation.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.sanitation.application.port.inbound.CleaningPolicyUseCase;
import br.com.brew.brassia.sanitation.application.port.outbound.CleaningPolicyRepository;
import br.com.brew.brassia.sanitation.domain.CleaningPolicy;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A auditoria é o histórico do parâmetro: ela já registra quem mudou, quando e para quanto. Não há
 * snapshot imutável como em `OperationalPreferences` porque os agregados guardam o que precisam no
 * momento da decisão — mudar o parâmetro afeta só o que vier depois.
 */
public final class CleaningPolicyHandler implements CleaningPolicyUseCase {

    private final CleaningPolicyRepository policies;
    private final AuditTrail audit;

    public CleaningPolicyHandler(CleaningPolicyRepository policies, AuditTrail audit) {
        this.policies = Objects.requireNonNull(policies);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public CleaningPolicy get(UUID breweryId) {
        return policies.find(breweryId);
    }

    @Override
    public CleaningPolicy update(UUID actorId, UUID breweryId, Integer validityHours) {
        var policy = policies.find(breweryId);
        policy.setValidityHours(validityHours);
        policies.save(policy);

        audit.record(AuditEvent.success(breweryId, actorId, "sanitation.policy.update",
                "sanitation.cleaning_policy", breweryId.toString(),
                Map.of("validityHours", String.valueOf(validityHours))));
        return policy;
    }
}
