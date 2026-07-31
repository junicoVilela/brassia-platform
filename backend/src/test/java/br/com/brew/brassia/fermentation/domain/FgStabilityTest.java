package br.com.brew.brassia.fermentation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FgStabilityTest {

    private static final UUID BREWERY = UUID.randomUUID();
    private static final UUID BATCH = UUID.randomUUID();
    private static final Instant START = Instant.parse("2026-07-28T08:00:00Z");
    private static final FgStabilityPolicy POLICY =
            new FgStabilityPolicy(48, 3, new BigDecimal("0.0020"));

    private static FermentationReading sg(String value, int hoursFromStart) {
        return FermentationReading.record(BREWERY, BATCH, ReadingKind.DENSITY, ReadingSource.SENSOR,
                new BigDecimal(value), "SG", START.plus(Duration.ofHours(hoursFromStart)));
    }

    @Test
    void stableWhenSeriesCoversWindowWithinTolerance() {
        var result = FgStability.evaluate(List.of(sg("1.0125", 0), sg("1.0120", 24), sg("1.0118", 48)), POLICY);

        assertThat(result.stable()).isTrue();
        assertThat(result.verdict()).isEqualTo(FgStabilityVerdict.STABLE);
        // O parecer explica o que usou.
        assertThat(result.readings()).hasSize(3);
        assertThat(result.span()).isEqualTo(Duration.ofHours(48));
        assertThat(result.amplitude()).isEqualByComparingTo("0.0007");
    }

    @Test
    void rejectsFalseStabilityWhenReadingsDoNotCoverTheWindow() {
        // Três leituras quase idênticas na mesma tarde: variação mínima, evidência nenhuma.
        var result = FgStability.evaluate(List.of(sg("1.0120", 0), sg("1.0119", 2), sg("1.0120", 4)), POLICY);

        assertThat(result.stable()).isFalse();
        assertThat(result.verdict()).isEqualTo(FgStabilityVerdict.WINDOW_NOT_COVERED);
        assertThat(result.span()).isEqualTo(Duration.ofHours(4));
    }

    @Test
    void rejectsWhenVariationExceedsTolerance() {
        var result = FgStability.evaluate(List.of(sg("1.0200", 0), sg("1.0150", 24), sg("1.0118", 48)), POLICY);

        assertThat(result.verdict()).isEqualTo(FgStabilityVerdict.VARIATION_ABOVE_TOLERANCE);
        assertThat(result.amplitude()).isEqualByComparingTo("0.0082");
    }

    @Test
    void requiresMinimumNumberOfReadings() {
        var result = FgStability.evaluate(List.of(sg("1.0120", 0), sg("1.0119", 48)), POLICY);

        assertThat(result.verdict()).isEqualTo(FgStabilityVerdict.INSUFFICIENT_READINGS);
    }

    @Test
    void considersOnlyTheMostRecentWindow() {
        // A leitura antiga e destoante fica fora da janela e não contamina o parecer.
        var result = FgStability.evaluate(
                List.of(sg("1.0500", -240), sg("1.0125", 0), sg("1.0120", 24), sg("1.0118", 48)), POLICY);

        assertThat(result.stable()).isTrue();
        assertThat(result.readings()).hasSize(3);
    }

    @Test
    void ignoresFlaggedReadingsAndOtherKinds() {
        var flagged = FermentationReading.record(BREWERY, BATCH, ReadingKind.DENSITY, ReadingSource.SENSOR,
                new BigDecimal("1.5000"), "SG", START.plus(Duration.ofHours(24)));
        var temperature = FermentationReading.record(BREWERY, BATCH, ReadingKind.TEMPERATURE, ReadingSource.SENSOR,
                new BigDecimal("18"), "C", START.plus(Duration.ofHours(24)));
        assertThat(flagged.valid()).isFalse();

        var result = FgStability.evaluate(
                List.of(sg("1.0125", 0), flagged, temperature, sg("1.0120", 24), sg("1.0118", 48)), POLICY);

        assertThat(result.stable()).isTrue();
        assertThat(result.readings()).hasSize(3);
    }

    @Test
    void ignoresDensityReadingsInAnotherUnit() {
        // A tolerância é declarada em SG; converter Plato aqui seria inventar comportamento.
        var plato = FermentationReading.record(BREWERY, BATCH, ReadingKind.DENSITY, ReadingSource.MANUAL,
                new BigDecimal("3.1"), "PLATO", START.plus(Duration.ofHours(24)));

        var result = FgStability.evaluate(List.of(sg("1.0125", 0), plato, sg("1.0118", 48)), POLICY);

        assertThat(result.verdict()).isEqualTo(FgStabilityVerdict.INSUFFICIENT_READINGS);
    }

    @Test
    void isStableExactlyAtTheToleranceBoundary() {
        var result = FgStability.evaluate(List.of(sg("1.0140", 0), sg("1.0130", 24), sg("1.0120", 48)), POLICY);

        assertThat(result.amplitude()).isEqualByComparingTo("0.0020");
        assertThat(result.stable()).isTrue();
    }

    @Test
    void emptySeriesIsNotStable() {
        var result = FgStability.evaluate(List.of(), POLICY);

        assertThat(result.verdict()).isEqualTo(FgStabilityVerdict.INSUFFICIENT_READINGS);
        assertThat(result.readings()).isEmpty();
        assertThat(result.span()).isZero();
    }

    @Test
    void rejectsInvalidPolicy() {
        assertThatThrownBy(() -> new FgStabilityPolicy(0, 3, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("janela");
        assertThatThrownBy(() -> new FgStabilityPolicy(48, 1, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("2 leituras");
        assertThatThrownBy(() -> new FgStabilityPolicy(48, 3, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tolerância");
    }
}
