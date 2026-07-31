package br.com.brew.brassia.fermentation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class YeastRecommendationTest {

    private static final UUID BREWERY = UUID.randomUUID();
    private static final UUID STRAIN = UUID.randomUUID();
    private static final UUID BATCH = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-07-31T10:00:00Z");
    private static final YeastPolicy POLICY = new YeastPolicy(10, 21, new BigDecimal("70"));

    /** Coleta aprovada, com a idade e a geração pedidas. */
    private static YeastHarvest approved(String viability, int ageDays, int generation) {
        var parentId = generation > 1 ? UUID.randomUUID() : null;
        var parentGeneration = generation > 1 ? generation - 1 : null;
        var harvest = YeastHarvest.collect(BREWERY, "LV-" + generation + "-" + ageDays, STRAIN, BATCH, parentId,
                parentGeneration, NOW.minus(Duration.ofDays(ageDays)), new BigDecimal(viability), "Creme",
                "Câmara 1", new BigDecimal("4"));
        harvest.approve(ACTOR, null, NOW);
        return harvest;
    }

    @Test
    void recommendsHarvestWithinEveryLimit() {
        var result = YeastRecommendation.evaluate(approved("92", 5, 3), POLICY, NOW);

        assertThat(result.recommended()).isTrue();
        assertThat(result.blockers()).isEmpty();
        assertThat(result.ageDays()).isEqualTo(5);
        // Explicável: um fator por critério, cada um com sua frase.
        assertThat(result.factors()).extracting(YeastRecommendation.Factor::name)
                .containsExactly("generation", "age", "viability");
        assertThat(result.factors()).allSatisfy(f -> assertThat(f.explanation()).isNotBlank());
    }

    @Test
    void blocksExhaustedLineageAndExplainsWhy() {
        var result = YeastRecommendation.evaluate(approved("95", 1, 11), POLICY, NOW);

        assertThat(result.recommended()).isFalse();
        assertThat(result.blockers()).hasSize(1);
        assertThat(result.blockers().getFirst()).contains("Geração 11", "linhagem esgotada");
    }

    @Test
    void blocksOldYeastAndExplainsWhy() {
        var result = YeastRecommendation.evaluate(approved("95", 30, 2), POLICY, NOW);

        assertThat(result.recommended()).isFalse();
        assertThat(result.blockers().getFirst()).contains("30 dia(s)", "velha demais");
    }

    @Test
    void blocksLowViabilityAndExplainsWhy() {
        var result = YeastRecommendation.evaluate(approved("55", 2, 2), POLICY, NOW);

        assertThat(result.recommended()).isFalse();
        assertThat(result.blockers().getFirst()).contains("55%", "abaixo do mínimo");
    }

    @Test
    void accumulatesEveryBlockerInsteadOfStoppingAtTheFirst() {
        var result = YeastRecommendation.evaluate(approved("40", 40, 12), POLICY, NOW);

        assertThat(result.recommended()).isFalse();
        assertThat(result.blockers()).hasSize(3);
    }

    @Test
    void limitsAreInclusive() {
        // Exatamente no limite ainda é recomendável.
        assertThat(YeastRecommendation.evaluate(approved("70", 21, 10), POLICY, NOW).recommended()).isTrue();
        assertThat(YeastRecommendation.evaluate(approved("69.99", 21, 10), POLICY, NOW).recommended()).isFalse();
        assertThat(YeastRecommendation.evaluate(approved("70", 22, 10), POLICY, NOW).recommended()).isFalse();
        assertThat(YeastRecommendation.evaluate(approved("70", 21, 11), POLICY, NOW).recommended()).isFalse();
    }

    @Test
    void ranksRecommendedFirstThenBestProspect() {
        var old = YeastRecommendation.evaluate(approved("95", 40, 1), POLICY, NOW);
        var good = YeastRecommendation.evaluate(approved("88", 3, 2), POLICY, NOW);
        var better = YeastRecommendation.evaluate(approved("95", 3, 4), POLICY, NOW);

        var ranked = YeastRecommendation.rank(List.of(old, good, better));

        assertThat(ranked.get(0)).isEqualTo(better);
        assertThat(ranked.get(1)).isEqualTo(good);
        assertThat(ranked.get(2)).isEqualTo(old);
    }

    @Test
    void reportsViabilityMarginAgainstTheMinimum() {
        assertThat(YeastRecommendation.evaluate(approved("92", 5, 2), POLICY, NOW).viabilityMargin(POLICY))
                .isEqualByComparingTo("22.00");
        assertThat(YeastRecommendation.evaluate(approved("60", 5, 2), POLICY, NOW).viabilityMargin(POLICY))
                .isEqualByComparingTo("-10.00");
    }

    @Test
    void validatesPolicy() {
        assertThatThrownBy(() -> new YeastPolicy(0, 21, new BigDecimal("70")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("geração máxima");
        assertThatThrownBy(() -> new YeastPolicy(10, 0, new BigDecimal("70")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("idade máxima");
        assertThatThrownBy(() -> new YeastPolicy(10, 21, new BigDecimal("101")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("viabilidade mínima");
        assertThat(YeastPolicy.defaults().maxGeneration()).isEqualTo(10);
    }

    @Test
    void confirmedUseConsumesTheHarvestAndLinksTheBatch() {
        var harvest = approved("92", 5, 2);
        var target = UUID.randomUUID();

        harvest.useIn(target, NOW);

        assertThat(harvest.status()).isEqualTo(YeastHarvestStatus.USED);
        assertThat(harvest.available()).isFalse();
        assertThat(harvest.pitchedBatchId()).isEqualTo(target);
        assertThat(harvest.pitchedAt()).isEqualTo(NOW);
    }

    @Test
    void theSameHarvestCannotBePitchedTwice() {
        var harvest = approved("92", 5, 2);
        harvest.useIn(UUID.randomUUID(), NOW);

        assertThatThrownBy(() -> harvest.useIn(UUID.randomUUID(), NOW))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("não está disponível");
    }

    @Test
    void unavailableHarvestCannotBeUsed() {
        var quarantined = YeastHarvest.collect(BREWERY, "LV-Q", STRAIN, BATCH, null, null, NOW,
                new BigDecimal("90"), "Creme", "Câmara 1", new BigDecimal("4"));
        assertThatThrownBy(() -> quarantined.useIn(UUID.randomUUID(), NOW))
                .isInstanceOf(IllegalStateException.class);

        var rejected = YeastHarvest.collect(BREWERY, "LV-R", STRAIN, BATCH, null, null, NOW,
                new BigDecimal("90"), "Creme", "Câmara 1", new BigDecimal("4"));
        rejected.reject(ACTOR, "Contaminação", NOW);
        assertThatThrownBy(() -> rejected.useIn(UUID.randomUUID(), NOW))
                .isInstanceOf(IllegalStateException.class);
    }
}
