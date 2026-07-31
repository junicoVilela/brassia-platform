package br.com.brew.brassia.fermentation.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * Parecer de estabilidade de FG (FER-003): além do veredito, carrega o critério aplicado e as
 * leituras que o sustentam, para o resultado ser explicável e conferível.
 */
public record FgStabilityResult(
        FgStabilityVerdict verdict,
        FgStabilityPolicy policy,
        List<FermentationReading> readings,
        Duration span,
        BigDecimal amplitude) {

    public FgStabilityResult {
        readings = List.copyOf(readings);
    }

    static FgStabilityResult stable(FgStabilityPolicy policy, List<FermentationReading> readings) {
        return of(FgStabilityVerdict.STABLE, policy, readings);
    }

    static FgStabilityResult notStable(FgStabilityVerdict verdict, FgStabilityPolicy policy,
            List<FermentationReading> readings) {
        return of(verdict, policy, readings);
    }

    private static FgStabilityResult of(FgStabilityVerdict verdict, FgStabilityPolicy policy,
            List<FermentationReading> readings) {
        return readings.size() < 2
                ? new FgStabilityResult(verdict, policy, readings, Duration.ZERO, BigDecimal.ZERO)
                : new FgStabilityResult(verdict, policy, readings, FgStability.span(readings),
                        FgStability.amplitude(readings));
    }

    public boolean stable() {
        return verdict.stable();
    }
}
