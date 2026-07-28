package br.com.brew.brassia.sanitation.adapter.inbound.web.dto;

import br.com.brew.brassia.sanitation.domain.ConsumptionSummary;
import java.math.BigDecimal;

public record ConsumptionSummaryView(
        String procedureCode, int cycleCount,
        BigDecimal avgWaterLiters, BigDecimal minWaterLiters, BigDecimal maxWaterLiters,
        BigDecimal avgEnergyKwh, BigDecimal minEnergyKwh, BigDecimal maxEnergyKwh,
        BigDecimal avgProductKg, BigDecimal minProductKg, BigDecimal maxProductKg) {

    public static ConsumptionSummaryView from(ConsumptionSummary s) {
        return new ConsumptionSummaryView(s.procedureCode(), s.cycleCount(),
                s.avgWaterLiters(), s.minWaterLiters(), s.maxWaterLiters(),
                s.avgEnergyKwh(), s.minEnergyKwh(), s.maxEnergyKwh(),
                s.avgProductKg(), s.minProductKg(), s.maxProductKg());
    }
}
