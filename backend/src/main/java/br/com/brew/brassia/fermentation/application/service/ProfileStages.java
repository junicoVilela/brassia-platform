package br.com.brew.brassia.fermentation.application.service;

import br.com.brew.brassia.fermentation.application.port.inbound.StageInput;
import br.com.brew.brassia.fermentation.domain.AdvanceCondition;
import br.com.brew.brassia.fermentation.domain.FermentationStage;
import java.util.List;

/** Converte entradas de estágio em estágios de domínio (validação no domínio). */
final class ProfileStages {

    private ProfileStages() {
    }

    static List<FermentationStage> from(List<StageInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("informe ao menos um estágio");
        }
        return inputs.stream()
                .map(i -> FermentationStage.of(i.sequence(), i.name(), i.targetTempC(), i.rampHours(),
                        i.pressurePsi(), AdvanceCondition.of(i.condition()), i.conditionDays(), i.targetGravity(),
                        i.requiresConfirmation()))
                .toList();
    }
}
