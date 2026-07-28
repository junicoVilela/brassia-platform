package br.com.brew.brassia.sanitation.domain;

import java.math.BigDecimal;

/**
 * Resumo consultivo de consumo por código de POP (CLN-005). Comparação read-only —
 * não altera nem reduz parâmetros do POP. Estatísticas nulas quando não há ciclos com
 * consumo registrado.
 */
public record ConsumptionSummary(
        String procedureCode, int cycleCount,
        BigDecimal avgWaterLiters, BigDecimal minWaterLiters, BigDecimal maxWaterLiters,
        BigDecimal avgEnergyKwh, BigDecimal minEnergyKwh, BigDecimal maxEnergyKwh,
        BigDecimal avgProductKg, BigDecimal minProductKg, BigDecimal maxProductKg) {

    public static ConsumptionSummary empty(String procedureCode) {
        return new ConsumptionSummary(procedureCode, 0, null, null, null, null, null, null, null, null, null);
    }
}
