package br.com.brew.brassia.sanitation.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/** Checagens da verificação (CLN-004); flags ausentes contam como reprovadas (fail-closed). */
public record VerificationRequest(
        Boolean rinseOk,
        Boolean visualOk,
        @NotNull @PositiveOrZero BigDecimal atpRlu,
        @NotNull @PositiveOrZero BigDecimal atpThreshold,
        Boolean microOk) {

    public boolean rinsePassed() {
        return Boolean.TRUE.equals(rinseOk);
    }

    public boolean visualPassed() {
        return Boolean.TRUE.equals(visualOk);
    }

    public boolean microPassed() {
        return Boolean.TRUE.equals(microOk);
    }
}
