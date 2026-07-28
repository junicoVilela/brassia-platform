package br.com.brew.brassia.sanitation.domain;

import java.math.BigDecimal;

/**
 * Medições registradas ao executar uma etapa do ciclo (CLN-003). {@code override}
 * pede o registro mesmo com parâmetro fora da ficha, exigindo justificativa; a alçada
 * para usá-lo é verificada no caso de uso (permissão sanitation.cycle.override).
 */
public record StepExecution(
        BigDecimal measuredConcentrationPct,
        BigDecimal measuredTempC,
        Integer measuredTimeMinutes,
        String flow,
        String evidence,
        String outOfOrderReason,
        boolean override,
        String overrideReason) {
}
