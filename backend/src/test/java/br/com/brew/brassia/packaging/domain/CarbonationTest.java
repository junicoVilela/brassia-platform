package br.com.brew.brassia.packaging.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CarbonationTest {

    private static final UUID PLAN = UUID.randomUUID();
    private static final UUID BREWERY = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final Instant AT = Instant.parse("2026-08-05T10:00:00Z");
    private static final String METHOD = "g = (vol_alvo − vol_residual) × V × 1,96 / rendimento";

    private static Carbonation priming(String target, String residual) {
        return Carbonation.priming(PLAN, BREWERY, new BigDecimal(target), new BigDecimal("20"),
                new BigDecimal(residual), PrimingSugar.SUCROSE, new BigDecimal("117.4"), METHOD, "1.0",
                List.of(), ACTOR, AT);
    }

    private static Carbonation forced(String target) {
        return Carbonation.forced(PLAN, BREWERY, new BigDecimal(target), new BigDecimal("4"),
                new BigDecimal("1.48"), new BigDecimal("0.81"), METHOD, "1.0", List.of(), ACTOR, AT);
    }

    @Test
    void primingKeepsInputsMethodAndConfirmation() {
        var carbonation = priming("2.4", "0.86");

        assertThat(carbonation.method()).isEqualTo(CarbonationMethod.PRIMING);
        assertThat(carbonation.primingSugar()).isEqualTo(PrimingSugar.SUCROSE);
        assertThat(carbonation.primingSugarGrams()).isEqualByComparingTo("117.4");
        assertThat(carbonation.pressureBar()).isNull();
        assertThat(carbonation.calculatorVersion()).isEqualTo("1.0");
        assertThat(carbonation.confirmedBy()).isEqualTo(ACTOR);
        assertThat(carbonation.confirmedAt()).isEqualTo(AT);
    }

    @Test
    void missingVolumesIsTheGapBetweenResidualAndTarget() {
        assertThat(priming("2.4", "0.86").missingVolumes()).isEqualByComparingTo("1.54");
    }

    @Test
    void refusesPrimingWhenResidualAlreadyReachesTheTarget() {
        // É assim que se estoura garrafa: açúcar sobre CO₂ que já estava lá.
        assertThatThrownBy(() -> priming("0.8", "0.86"))
                .isInstanceOf(OverCarbonationException.class)
                .satisfies(e -> {
                    var ex = (OverCarbonationException) e;
                    assertThat(ex.targetVolumes()).isEqualByComparingTo("0.8");
                    assertThat(ex.residualVolumes()).isEqualByComparingTo("0.86");
                });
        assertThatThrownBy(() -> priming("0.86", "0.86")).isInstanceOf(OverCarbonationException.class);
    }

    @Test
    void forcedCarbonationKeepsPressureAndNoSugar() {
        var carbonation = forced("2.5");

        assertThat(carbonation.method()).isEqualTo(CarbonationMethod.FORCED);
        assertThat(carbonation.pressureBar()).isEqualByComparingTo("0.81");
        assertThat(carbonation.primingSugar()).isNull();
        assertThat(carbonation.primingSugarGrams()).isNull();
    }

    @Test
    void forcedCarbonationIsAllowedEvenWhenResidualPassesTheTarget() {
        // Ao contrário do priming, forçada com alvo abaixo do residual só significa não aplicar
        // pressão — nada é adicionado à cerveja, então não há risco de sobrepressão.
        var carbonation = Carbonation.forced(PLAN, BREWERY, new BigDecimal("1.0"), new BigDecimal("0"),
                new BigDecimal("1.48"), BigDecimal.ZERO, METHOD, "1.0", List.of(), ACTOR, AT);

        assertThat(carbonation.pressureBar()).isEqualByComparingTo("0");
        assertThat(carbonation.missingVolumes()).isEqualByComparingTo("0");
    }

    @Test
    void refusesMixingTheTwoMethods() {
        assertThatThrownBy(() -> Carbonation.reconstitute(PLAN, BREWERY, CarbonationMethod.PRIMING,
                new BigDecimal("2.4"), new BigDecimal("20"), new BigDecimal("0.86"), PrimingSugar.SUCROSE,
                new BigDecimal("117"), new BigDecimal("0.8"), METHOD, "1.0", List.of(), ACTOR, AT, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("priming não tem pressão");

        assertThatThrownBy(() -> Carbonation.reconstitute(PLAN, BREWERY, CarbonationMethod.FORCED,
                new BigDecimal("2.4"), new BigDecimal("4"), new BigDecimal("1.48"), PrimingSugar.SUCROSE,
                new BigDecimal("117"), new BigDecimal("0.8"), METHOD, "1.0", List.of(), ACTOR, AT, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não usa açúcar de priming");
    }

    @Test
    void requiresTargetTemperatureResidualAndConfirmation() {
        assertThatThrownBy(() -> priming("0", "0.5")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Carbonation.priming(PLAN, BREWERY, new BigDecimal("2.4"), null,
                new BigDecimal("0.86"), PrimingSugar.SUCROSE, new BigDecimal("117"), METHOD, "1.0", List.of(),
                ACTOR, AT))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("temperatura");
        assertThatThrownBy(() -> Carbonation.priming(PLAN, BREWERY, new BigDecimal("2.4"), new BigDecimal("20"),
                new BigDecimal("0.86"), PrimingSugar.SUCROSE, new BigDecimal("117"), METHOD, "1.0", List.of(),
                null, AT))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("confirmação");
    }

    @Test
    void sugarYieldsAreStoichiometricExceptDryMaltExtract() {
        assertThat(PrimingSugar.SUCROSE.yieldGramsCo2PerGram()).isEqualByComparingTo("0.514");
        assertThat(PrimingSugar.SUCROSE.approximate()).isFalse();
        assertThat(PrimingSugar.DEXTROSE_MONOHYDRATE.yieldGramsCo2PerGram()).isEqualByComparingTo("0.444");
        assertThat(PrimingSugar.DEXTROSE_MONOHYDRATE.approximate()).isFalse();
        // Rendimento do DME depende da fermentabilidade do extrato: sai com aviso.
        assertThat(PrimingSugar.DRY_MALT_EXTRACT.approximate()).isTrue();
    }

    @Test
    void parsesMethodAndSugarFromText() {
        assertThat(CarbonationMethod.of("forced")).isEqualTo(CarbonationMethod.FORCED);
        assertThat(PrimingSugar.of("sucrose")).isEqualTo(PrimingSugar.SUCROSE);
        assertThatThrownBy(() -> CarbonationMethod.of("nenhum")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PrimingSugar.of(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
