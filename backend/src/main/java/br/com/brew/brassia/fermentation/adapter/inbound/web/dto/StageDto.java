package br.com.brew.brassia.fermentation.adapter.inbound.web.dto;

import br.com.brew.brassia.fermentation.application.port.inbound.StageInput;
import br.com.brew.brassia.fermentation.domain.FermentationStage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** Estágio do perfil (entrada e saída partilham os mesmos campos tipados). */
public record StageDto(
        @Positive int sequence,
        @NotBlank String name,
        @NotNull BigDecimal targetTempC,
        Integer rampHours,
        BigDecimal pressurePsi,
        @NotBlank String condition,
        Integer conditionDays,
        BigDecimal targetGravity,
        boolean requiresConfirmation) {

    public StageInput toInput() {
        return new StageInput(sequence, name, targetTempC, rampHours, pressurePsi, condition, conditionDays,
                targetGravity, requiresConfirmation);
    }

    public static StageDto from(FermentationStage s) {
        return new StageDto(s.sequence(), s.name(), s.targetTempC(), s.rampHours(), s.pressurePsi(),
                s.condition().name(), s.conditionDays(), s.targetGravity(), s.requiresConfirmation());
    }
}
