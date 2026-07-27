package br.com.brew.brassia.production.adapter.inbound.web.dto;

import br.com.brew.brassia.production.domain.AppliedCorrection;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AppliedCorrectionView(
        UUID id, String calculator, UUID sourceMeasurementId, String note, Map<String, BigDecimal> inputs,
        BigDecimal plannedValue, String plannedUnit, BigDecimal realizedValue, Instant appliedAt) {

    public static AppliedCorrectionView from(AppliedCorrection c) {
        return new AppliedCorrectionView(c.id(), c.calculator(), c.sourceMeasurementId(), c.note(), c.inputs(),
                c.plannedValue(), c.plannedUnit(), c.realizedValue(), c.appliedAt());
    }
}
