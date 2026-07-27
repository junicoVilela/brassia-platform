package br.com.brew.brassia.sanitation.adapter.inbound.web.dto;

import br.com.brew.brassia.sanitation.application.port.inbound.ProcedureStepInput;
import br.com.brew.brassia.sanitation.domain.ProcedureStep;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** Etapa do POP (entrada e saída partilham os mesmos campos tipados). */
public record ProcedureStepDto(
        @Positive int sequence,
        @NotBlank String method,
        String product,
        BigDecimal concentrationMinPct,
        BigDecimal concentrationMaxPct,
        BigDecimal tempMinC,
        BigDecimal tempMaxC,
        Integer timeMinutes,
        String flow,
        String ppe,
        String alternative,
        String prohibition,
        boolean evidenceRequired) {

    public ProcedureStepInput toInput() {
        return new ProcedureStepInput(sequence, method, product, concentrationMinPct, concentrationMaxPct,
                tempMinC, tempMaxC, timeMinutes, flow, ppe, alternative, prohibition, evidenceRequired);
    }

    public static ProcedureStepDto from(ProcedureStep s) {
        return new ProcedureStepDto(s.sequence(), s.method(), s.product(), s.concentrationMinPct(),
                s.concentrationMaxPct(), s.tempMinC(), s.tempMaxC(), s.timeMinutes(), s.flow(), s.ppe(),
                s.alternative(), s.prohibition(), s.evidenceRequired());
    }
}
