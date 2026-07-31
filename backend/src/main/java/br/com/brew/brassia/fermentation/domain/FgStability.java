package br.com.brew.brassia.fermentation.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Avaliação de estabilidade de FG (FER-003). É um parecer: explica quais leituras usou e por
 * que concluiu o que concluiu, e <strong>nunca</strong> encerra a fermentação — quem decide é
 * o cervejeiro.
 *
 * <p>O caso perigoso é o <em>FG falso estável</em>: três leituras próximas na mesma tarde
 * variam pouco por construção. Por isso a série considerada precisa cobrir a janela inteira,
 * não apenas caber dentro dela.
 */
public final class FgStability {

    private FgStability() {
    }

    /**
     * Avalia a série do lote. Só entram leituras de densidade em SG marcadas como válidas —
     * as sinalizadas pela FER-002 são ruído de sensor e não sustentam um parecer.
     */
    public static FgStabilityResult evaluate(List<FermentationReading> series, FgStabilityPolicy policy) {
        var usable = series.stream()
                .filter(r -> r.kind() == ReadingKind.DENSITY && r.valid() && "SG".equals(r.unit()))
                .sorted(Comparator.comparing(FermentationReading::measuredAt))
                .toList();

        if (usable.size() < policy.minReadings()) {
            return FgStabilityResult.notStable(FgStabilityVerdict.INSUFFICIENT_READINGS, policy, usable);
        }

        var last = usable.getLast().measuredAt();
        var windowStart = last.minus(Duration.ofHours(policy.windowHours()));
        var considered = usable.stream().filter(r -> !r.measuredAt().isBefore(windowStart)).toList();

        if (considered.size() < policy.minReadings()) {
            return FgStabilityResult.notStable(FgStabilityVerdict.INSUFFICIENT_READINGS, policy, considered);
        }
        // A janela precisa estar coberta: leituras aglomeradas num intervalo curto não provam nada.
        if (span(considered).compareTo(Duration.ofHours(policy.windowHours())) < 0) {
            return FgStabilityResult.notStable(FgStabilityVerdict.WINDOW_NOT_COVERED, policy, considered);
        }
        if (amplitude(considered).compareTo(policy.toleranceSg()) > 0) {
            return FgStabilityResult.notStable(FgStabilityVerdict.VARIATION_ABOVE_TOLERANCE, policy, considered);
        }
        return FgStabilityResult.stable(policy, considered);
    }

    static Duration span(List<FermentationReading> readings) {
        return Duration.between(readings.getFirst().measuredAt(), readings.getLast().measuredAt());
    }

    static BigDecimal amplitude(List<FermentationReading> readings) {
        var values = readings.stream().map(FermentationReading::value).toList();
        var min = values.stream().min(BigDecimal::compareTo).orElseThrow();
        var max = values.stream().max(BigDecimal::compareTo).orElseThrow();
        return max.subtract(min);
    }

    static Instant lastMeasuredAt(List<FermentationReading> readings) {
        return readings.isEmpty() ? null : readings.getLast().measuredAt();
    }
}
