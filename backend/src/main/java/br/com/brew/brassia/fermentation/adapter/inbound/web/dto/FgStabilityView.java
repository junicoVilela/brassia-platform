package br.com.brew.brassia.fermentation.adapter.inbound.web.dto;

import br.com.brew.brassia.fermentation.domain.FgStabilityResult;
import java.math.BigDecimal;
import java.util.List;

/**
 * Parecer de estabilidade de FG (FER-003). Devolve o critério aplicado e as leituras usadas
 * junto do veredito para o resultado ser conferível — e nunca implica encerramento.
 */
public record FgStabilityView(
        boolean stable,
        String verdict,
        StabilityDto policy,
        long spanHours,
        BigDecimal amplitudeSg,
        List<ReadingView> readings) {

    public static FgStabilityView from(FgStabilityResult r) {
        return new FgStabilityView(
                r.stable(),
                r.verdict().name(),
                new StabilityDto(r.policy().windowHours(), r.policy().minReadings(), r.policy().toleranceSg()),
                r.span().toHours(),
                r.amplitude(),
                r.readings().stream().map(ReadingView::from).toList());
    }
}
