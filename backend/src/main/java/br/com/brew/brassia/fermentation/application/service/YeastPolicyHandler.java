package br.com.brew.brassia.fermentation.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.fermentation.application.port.inbound.YeastPolicyUseCase;
import br.com.brew.brassia.fermentation.application.port.outbound.YeastPolicyRepository;
import br.com.brew.brassia.fermentation.domain.YeastPolicy;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Política de reutilização da cervejaria (YST-002); campo omitido herda o padrão do domínio. */
public final class YeastPolicyHandler implements YeastPolicyUseCase {

    private final YeastPolicyRepository policies;
    private final AuditTrail audit;

    public YeastPolicyHandler(YeastPolicyRepository policies, AuditTrail audit) {
        this.policies = Objects.requireNonNull(policies);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public YeastPolicy get(UUID breweryId) {
        return policies.find(breweryId).orElseGet(YeastPolicy::defaults);
    }

    @Override
    public void save(UUID actorId, UUID breweryId, Integer maxGeneration, Integer maxAgeDays,
            BigDecimal minViability) {
        var defaults = YeastPolicy.defaults();
        var policy = new YeastPolicy(
                maxGeneration == null ? defaults.maxGeneration() : maxGeneration,
                maxAgeDays == null ? defaults.maxAgeDays() : maxAgeDays,
                minViability == null ? defaults.minViabilityPercent() : minViability);
        policies.save(breweryId, policy);

        audit.record(AuditEvent.success(breweryId, actorId, "fermentation.yeast.policy.save",
                "fermentation.yeast.policy", breweryId.toString(),
                Map.of("maxGeneration", String.valueOf(policy.maxGeneration()),
                        "maxAgeDays", String.valueOf(policy.maxAgeDays()),
                        "minViability", policy.minViabilityPercent().toPlainString())));
    }
}
