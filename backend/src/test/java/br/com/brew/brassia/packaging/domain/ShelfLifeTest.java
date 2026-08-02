package br.com.brew.brassia.packaging.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShelfLifeTest {

    private static final UUID PLAN = UUID.randomUUID();
    private static final UUID BREWERY = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final Instant AT = Instant.parse("2026-08-20T16:00:00Z");
    private static final LocalDate PACKAGED = LocalDate.parse("2026-08-20");

    /** Política da casa: quanto mais limpo o envase, mais dias ele sustenta. */
    private static ShelfLifePolicy policy() {
        return new ShelfLifePolicy(List.of(
                new ShelfLifePolicy.Tier(new BigDecimal("50"), 180),
                new ShelfLifePolicy.Tier(new BigDecimal("100"), 120),
                new ShelfLifePolicy.Tier(new BigDecimal("200"), 60)), 30);
    }

    private static OxygenMeasurement measurement(String doPpb, String tpoPpb, boolean purged, boolean sealed) {
        return new OxygenMeasurement(new BigDecimal(doPpb), new BigDecimal(tpoPpb), "purga com CO₂ pré-enchimento",
                purged, "recravação medida", sealed);
    }

    // --- política ---

    @Test
    void tierMatchesTheFirstRangeThatHoldsTheMeasuredTpo() {
        var policy = policy();

        assertThat(policy.shelfLifeDaysFor(new BigDecimal("30"))).isEqualTo(180);
        assertThat(policy.shelfLifeDaysFor(new BigDecimal("50"))).isEqualTo(180);
        assertThat(policy.shelfLifeDaysFor(new BigDecimal("51"))).isEqualTo(120);
        assertThat(policy.shelfLifeDaysFor(new BigDecimal("150"))).isEqualTo(60);
    }

    @Test
    void tpoAboveEveryTierFallsToTheWorstCase() {
        assertThat(policy().shelfLifeDaysFor(new BigDecimal("400"))).isEqualTo(30);
        assertThat(policy().tierFor(new BigDecimal("400"))).isEmpty();
    }

    @Test
    void policyOrdersTiersRegardlessOfHowTheyWereInformed() {
        var policy = new ShelfLifePolicy(List.of(
                new ShelfLifePolicy.Tier(new BigDecimal("200"), 60),
                new ShelfLifePolicy.Tier(new BigDecimal("50"), 180)), 30);

        assertThat(policy.tiers()).extracting(ShelfLifePolicy.Tier::maxTpoPpb)
                .containsExactly(new BigDecimal("50"), new BigDecimal("200"));
    }

    @Test
    void refusesPolicyWhereMoreOxygenBuysMoreShelfLife() {
        assertThatThrownBy(() -> new ShelfLifePolicy(List.of(
                new ShelfLifePolicy.Tier(new BigDecimal("50"), 60),
                new ShelfLifePolicy.Tier(new BigDecimal("200"), 180)), 30))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mais oxigênio não pode render mais validade");
    }

    @Test
    void refusesEmptyPolicyDuplicatedTiersAndOptimisticFallback() {
        assertThatThrownBy(() -> new ShelfLifePolicy(List.of(), 30))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ShelfLifePolicy(List.of(
                new ShelfLifePolicy.Tier(new BigDecimal("50"), 180),
                new ShelfLifePolicy.Tier(new BigDecimal("50"), 120)), 30))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repetidas");
        // Pior caso não pode ser melhor que a pior faixa.
        assertThatThrownBy(() -> new ShelfLifePolicy(List.of(
                new ShelfLifePolicy.Tier(new BigDecimal("200"), 60)), 90))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- medição ---

    @Test
    void headspaceIsWhatTheTotalHasBeyondTheDissolved() {
        assertThat(measurement("30", "80", true, true).headspaceOxygenPpb()).isEqualByComparingTo("50");
    }

    @Test
    void refusesTotalOxygenBelowDissolved() {
        // O total inclui o dissolvido: TPO < DO é erro de leitura ou de unidade.
        assertThatThrownBy(() -> measurement("80", "30", true, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("o total inclui o dissolvido");
    }

    @Test
    void requiresPurgeAndSealMethodsAndNonNegativeValues() {
        assertThatThrownBy(() -> new OxygenMeasurement(new BigDecimal("-1"), new BigDecimal("80"), "purga",
                true, "recravação", true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OxygenMeasurement(new BigDecimal("30"), new BigDecimal("80"), " ",
                true, "recravação", true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OxygenMeasurement(new BigDecimal("30"), new BigDecimal("80"), "purga",
                true, "", true)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void evidenceIsCompleteOnlyWithPurgeAndSeal() {
        assertThat(measurement("30", "80", true, true).evidenceComplete()).isTrue();
        assertThat(measurement("30", "80", false, true).evidenceComplete()).isFalse();
        assertThat(measurement("30", "80", true, false).evidenceComplete()).isFalse();
    }

    // --- recomendação ---

    @Test
    void recommendationExplainsWhichTierMatched() {
        var recommendation = ShelfLifeRecommendation.evaluate(
                measurement("30", "80", true, true), policy(), PACKAGED);

        assertThat(recommendation.shelfLifeDays()).isEqualTo(120);
        assertThat(recommendation.bestBefore()).isEqualTo(PACKAGED.plusDays(120));
        assertThat(recommendation.withinPolicyTiers()).isTrue();
        assertThat(recommendation.matchedTierMaxTpoPpb()).isEqualByComparingTo("100");
        assertThat(recommendation.caveats()).isEmpty();
        assertThat(recommendation.factors()).extracting(ShelfLifeRecommendation.Factor::name)
                .containsExactly("tpo", "dissolvedOxygen", "purge", "seal");
    }

    @Test
    void unverifiedPurgeAndFailedSealBecomeCaveatsWithoutChangingTheNumber() {
        var trusted = ShelfLifeRecommendation.evaluate(measurement("30", "80", true, true), policy(), PACKAGED);
        var doubtful = ShelfLifeRecommendation.evaluate(measurement("30", "80", false, false), policy(), PACKAGED);

        // A evidência incompleta não inventa outro número: ela reduz a confiança nele.
        assertThat(doubtful.shelfLifeDays()).isEqualTo(trusted.shelfLifeDays());
        assertThat(doubtful.caveats()).hasSize(2);
        assertThat(doubtful.caveats()).anyMatch(c -> c.contains("Purga não conferida"));
        assertThat(doubtful.caveats()).anyMatch(c -> c.contains("Vedação reprovada"));
    }

    @Test
    void tpoAboveEveryTierIsSaidOutLoud() {
        var recommendation = ShelfLifeRecommendation.evaluate(
                measurement("100", "400", true, true), policy(), PACKAGED);

        assertThat(recommendation.shelfLifeDays()).isEqualTo(30);
        assertThat(recommendation.withinPolicyTiers()).isFalse();
        assertThat(recommendation.matchedTierMaxTpoPpb()).isNull();
        assertThat(recommendation.caveats()).anyMatch(c -> c.contains("acima de todas as faixas"));
    }

    // --- registro e override ---

    @Test
    void recordKeepsRecommendationAsTheEffectiveShelfLife() {
        var recommendation = ShelfLifeRecommendation.evaluate(
                measurement("30", "80", true, true), policy(), PACKAGED);
        var record = FreshnessRecord.record(PLAN, BREWERY, PACKAGED, measurement("30", "80", true, true),
                recommendation, ACTOR, AT);

        assertThat(record.effectiveShelfLifeDays()).isEqualTo(120);
        assertThat(record.effectiveBestBefore()).isEqualTo(PACKAGED.plusDays(120));
        assertThat(record.overridden()).isFalse();
    }

    @Test
    void recordWithoutPolicyKeepsTheEvidenceAndLeavesShelfLifeOpen() {
        var record = FreshnessRecord.record(PLAN, BREWERY, PACKAGED, measurement("30", "80", true, true),
                null, ACTOR, AT);

        // Evidência não se descarta; a validade é que fica a decidir.
        assertThat(record.measurement().totalPackageOxygenPpb()).isEqualByComparingTo("80");
        assertThat(record.recommendedBestBefore()).isNull();
        assertThat(record.effectiveBestBefore()).isNull();
    }

    @Test
    void overrideNeverErasesTheRecommendation() {
        var recommendation = ShelfLifeRecommendation.evaluate(
                measurement("30", "80", true, true), policy(), PACKAGED);
        var record = FreshnessRecord.record(PLAN, BREWERY, PACKAGED, measurement("30", "80", true, true),
                recommendation, ACTOR, AT);

        record.override(180, "lote destinado a estoque refrigerado", ACTOR, AT);

        assertThat(record.recommendedShelfLifeDays()).isEqualTo(120);
        assertThat(record.overrideShelfLifeDays()).isEqualTo(180);
        assertThat(record.effectiveBestBefore()).isEqualTo(PACKAGED.plusDays(180));
        assertThat(record.overrideReason()).isEqualTo("lote destinado a estoque refrigerado");
        assertThat(record.overriddenBy()).isEqualTo(ACTOR);
        // Override que estende além da evidência é o que mais precisa de justificativa.
        assertThat(record.extendsBeyondRecommendation()).isTrue();
    }

    @Test
    void overrideThatShortensIsNotFlaggedAsExtending() {
        var recommendation = ShelfLifeRecommendation.evaluate(
                measurement("30", "80", true, true), policy(), PACKAGED);
        var record = FreshnessRecord.record(PLAN, BREWERY, PACKAGED, measurement("30", "80", true, true),
                recommendation, ACTOR, AT);

        record.override(60, "cliente pediu validade menor", ACTOR, AT);

        assertThat(record.extendsBeyondRecommendation()).isFalse();
        assertThat(record.effectiveShelfLifeDays()).isEqualTo(60);
    }

    @Test
    void overrideRequiresReasonAndPositiveShelfLife() {
        var record = FreshnessRecord.record(PLAN, BREWERY, PACKAGED, measurement("30", "80", true, true),
                null, ACTOR, AT);

        assertThatThrownBy(() -> record.override(120, " ", ACTOR, AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> record.override(0, "motivo", ACTOR, AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(record.overridden()).isFalse();
    }
}
