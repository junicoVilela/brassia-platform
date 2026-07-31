package br.com.brew.brassia.fermentation.adapter.inbound.web.dto;

import br.com.brew.brassia.fermentation.application.port.inbound.StabilityInput;
import br.com.brew.brassia.fermentation.domain.FgStabilityPolicy;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** Critério de estabilidade de FG do perfil (FER-003); omitido → padrão do domínio. */
public record StabilityDto(
        @Positive Integer windowHours,
        @Positive Integer minReadings,
        @Positive BigDecimal toleranceSg) {

    public StabilityInput toInput() {
        return new StabilityInput(windowHours, minReadings, toleranceSg);
    }

    public static StabilityDto from(FgStabilityPolicy p) {
        return new StabilityDto(p.windowHours(), p.minReadings(), p.toleranceSg());
    }
}
