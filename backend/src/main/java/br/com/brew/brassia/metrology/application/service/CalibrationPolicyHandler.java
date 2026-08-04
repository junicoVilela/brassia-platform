package br.com.brew.brassia.metrology.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.metrology.application.port.inbound.CalibrationPolicyUseCase;
import br.com.brew.brassia.metrology.application.port.outbound.CalibrationPolicyRepository;
import br.com.brew.brassia.metrology.domain.CalibrationPolicy;
import br.com.brew.brassia.metrology.domain.InstrumentType;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class CalibrationPolicyHandler implements CalibrationPolicyUseCase {

    private final CalibrationPolicyRepository policies;
    private final AuditTrail audit;

    public CalibrationPolicyHandler(CalibrationPolicyRepository policies, AuditTrail audit) {
        this.policies = Objects.requireNonNull(policies);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public CalibrationPolicy get(UUID breweryId) {
        return policies.find(breweryId);
    }

    @Override
    public CalibrationPolicy replace(UUID actorId, UUID breweryId, Map<String, Integer> monthsByType) {
        var policy = CalibrationPolicy.none(breweryId);
        monthsByType.forEach((type, months) -> policy.set(InstrumentType.valueOf(type), months));
        policies.save(policy);

        audit.record(AuditEvent.success(breweryId, actorId, "metrology.policy.update",
                "metrology.calibration_policy", breweryId.toString(),
                Map.of("types", String.valueOf(policy.monthsByType().size()))));
        return policy;
    }
}
