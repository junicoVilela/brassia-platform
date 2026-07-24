package br.com.brew.brassia.water.adapter.inbound.web.dto;

import br.com.brew.brassia.water.domain.IonProfile;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ChargeBalanceRequest(
        @NotNull BigDecimal calcium,
        @NotNull BigDecimal magnesium,
        @NotNull BigDecimal sodium,
        @NotNull BigDecimal sulfate,
        @NotNull BigDecimal chloride,
        @NotNull BigDecimal bicarbonate) {

    public IonProfile toIons() {
        return new IonProfile(calcium, magnesium, sodium, sulfate, chloride, bicarbonate);
    }
}
