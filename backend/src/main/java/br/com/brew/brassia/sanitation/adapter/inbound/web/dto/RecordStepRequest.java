package br.com.brew.brassia.sanitation.adapter.inbound.web.dto;

import jakarta.validation.constraints.Min;
import java.math.BigDecimal;

public record RecordStepRequest(
        @Min(1) int sequence,
        BigDecimal measuredConcentrationPct,
        BigDecimal measuredTempC,
        Integer measuredTimeMinutes,
        String flow,
        String evidence,
        String outOfOrderReason,
        Boolean override,
        String overrideReason) {

    /** {@code true} apenas quando o cliente pediu explicitamente o override. */
    public boolean overrideRequested() {
        return Boolean.TRUE.equals(override);
    }
}
