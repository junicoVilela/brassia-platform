package br.com.brew.brassia.sanitation.application.service;

import br.com.brew.brassia.sanitation.application.port.inbound.ProcedureStepInput;
import br.com.brew.brassia.sanitation.domain.ProcedureStep;
import java.util.List;

/** Converte entradas de etapa em etapas de domínio (validação no domínio). */
final class ProcedureSteps {

    private ProcedureSteps() {
    }

    static List<ProcedureStep> from(List<ProcedureStepInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("informe ao menos uma etapa");
        }
        return inputs.stream()
                .map(i -> ProcedureStep.of(i.sequence(), i.method(), i.product(), i.concentrationMinPct(),
                        i.concentrationMaxPct(), i.tempMinC(), i.tempMaxC(), i.timeMinutes(), i.flow(), i.ppe(),
                        i.alternative(), i.prohibition(), i.evidenceRequired()))
                .toList();
    }
}
