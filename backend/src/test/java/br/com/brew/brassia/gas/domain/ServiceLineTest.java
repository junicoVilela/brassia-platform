package br.com.brew.brassia.gas.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ServiceLineTest {

    private static final UUID BREWERY = UUID.randomUUID();
    private static final UUID POINT = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final Instant AT = Instant.parse("2026-08-25T10:00:00Z");
    private static final String METHOD = "L = (P − ρ·g·h − P_residual) / (R × vazão/vazão_ref)";

    private static LineResistance tubing() {
        return LineResistance.register(BREWERY, "vinil", new BigDecimal("4.8"), new BigDecimal("0.679"),
                new BigDecimal("1"));
    }

    private static LineBalance balance(String lengthMeters, BigDecimal networkMax) {
        return LineBalance.of(new BigDecimal("0.81"), new BigDecimal(lengthMeters), new BigDecimal("0.0302"),
                new BigDecimal("0.679"), new BigDecimal("1"), new BigDecimal("4"), new BigDecimal("2.5"),
                tubing(), networkMax, METHOD, "1.0", List.of());
    }

    // --- resistência ---

    @Test
    void tubingKeepsTheReferenceFlowNextToTheResistance() {
        var tubing = tubing();

        assertThat(tubing.material()).isEqualTo("vinil");
        assertThat(tubing.internalDiameterMm()).isEqualByComparingTo("4.8");
        assertThat(tubing.resistanceBarPerMeter()).isEqualByComparingTo("0.679");
        // Sem a vazão de referência não dá para escalar a resistência para outra vazão.
        assertThat(tubing.referenceFlowLpm()).isEqualByComparingTo("1");
    }

    @Test
    void tubingRefusesNonPositiveNumbersAndEmptyMaterial() {
        assertThatThrownBy(() -> LineResistance.register(BREWERY, "vinil", BigDecimal.ZERO,
                new BigDecimal("0.679"), BigDecimal.ONE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LineResistance.register(BREWERY, " ", new BigDecimal("4.8"),
                new BigDecimal("0.679"), BigDecimal.ONE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LineResistance.register(BREWERY, "vinil", new BigDecimal("4.8"),
                new BigDecimal("0.679"), BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }

    // --- recomendação ---

    @Test
    void everyRecommendationCarriesTheManualAdjustmentWarning() {
        var balance = balance("1.05", null);

        assertThat(balance.feasible()).isTrue();
        assertThat(balance.recommendedLengthMeters()).isEqualByComparingTo("1.05");
        // O sistema calcula; quem ajusta válvula e regulador é a pessoa.
        assertThat(balance.warnings()).contains(LineBalance.MANUAL_ADJUSTMENT_ONLY);
        assertThat(balance.safetyWarnings()).isNotEmpty();
    }

    @Test
    void recommendationKeepsTheInputsThatDefineIt() {
        var balance = balance("1.05", null);

        assertThat(balance.material()).isEqualTo("vinil");
        assertThat(balance.internalDiameterMm()).isEqualByComparingTo("4.8");
        assertThat(balance.servingTempC()).isEqualByComparingTo("4");
        assertThat(balance.targetCo2Volumes()).isEqualByComparingTo("2.5");
        assertThat(balance.targetFlowLpm()).isEqualByComparingTo("1");
        assertThat(balance.calculatorVersion()).isEqualTo("1.0");
        assertThat(balance.calculationMethod()).isEqualTo(METHOD);
    }

    @Test
    void impossibleSetupIsFlaggedAsUnfeasibleWithASafetyWarning() {
        var balance = balance("0", null);

        assertThat(balance.feasible()).isFalse();
        assertThat(balance.warnings()).extracting(LineBalance.Warning::code)
                .contains("no_balance_possible");
        assertThat(balance.safetyWarnings()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void pressureAboveTheGasNetworkLimitIsASafetyWarning() {
        // A rede que serve o ponto aguenta 0,5 bar, mas servir a carbonatação pede 0,81.
        var balance = balance("1.05", new BigDecimal("0.5"));

        assertThat(balance.warnings()).extracting(LineBalance.Warning::code).contains("above_network_limit");
        assertThat(balance.safetyWarnings()).anyMatch(w -> w.code().equals("above_network_limit"));
    }

    @Test
    void pressureWithinTheNetworkLimitRaisesNoLimitWarning() {
        var balance = balance("1.05", new BigDecimal("3"));

        assertThat(balance.warnings()).extracting(LineBalance.Warning::code)
                .doesNotContain("above_network_limit");
    }

    @Test
    void calculatorAlertsBecomeNonSafetyWarnings() {
        var balance = LineBalance.of(new BigDecimal("0.81"), new BigDecimal("1.05"), new BigDecimal("0.03"),
                new BigDecimal("0.679"), BigDecimal.ONE, new BigDecimal("4"), new BigDecimal("2.5"), tubing(),
                null, METHOD, "1.0", List.of("resistência medida em outra vazão"));

        assertThat(balance.warnings()).anyMatch(w -> !w.safety() && w.code().equals("calculation_alert"));
    }

    // --- linha e revisões ---

    @Test
    void newLineHasNoAppliedAssembly() {
        var line = ServiceLine.register(BREWERY, "LN-01", "Torneira 1", POINT);

        assertThat(line.currentRevision()).isZero();
        assertThat(line.everApplied()).isFalse();
    }

    @Test
    void applyingAssemblyAdvancesTheRevision() {
        var line = ServiceLine.register(BREWERY, "LN-01", "Torneira 1", POINT);

        assertThat(line.applyRevision()).isEqualTo(1);
        assertThat(line.applyRevision()).isEqualTo(2);
        assertThat(line.currentRevision()).isEqualTo(2);
        assertThat(line.everApplied()).isTrue();
    }

    @Test
    void revisionKeepsWhatWasBuiltNextToWhatWasRecommended() {
        // Montaram 1,20 m onde a recomendação pedia 1,05: o desvio fica explícito.
        var revision = revision("1.20", "1.05");

        assertThat(revision.appliedLengthMeters()).isEqualByComparingTo("1.20");
        assertThat(revision.recommendedLengthMeters()).isEqualByComparingTo("1.05");
        assertThat(revision.lengthDeviationMeters()).isEqualByComparingTo("0.15");
    }

    @Test
    void revisionStartsAtOneAndRequiresItsMeasurements() {
        assertThatThrownBy(() -> new ServiceLine.Revision(UUID.randomUUID(), UUID.randomUUID(), BREWERY, 0,
                "vinil", new BigDecimal("4.8"), new BigDecimal("1.2"), new BigDecimal("1.05"),
                new BigDecimal("0.81"), new BigDecimal("0.305"), new BigDecimal("0.069"), BigDecimal.ONE,
                new BigDecimal("4"), new BigDecimal("2.5"), METHOD, "1.0", null, ACTOR, AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("começa em 1");

        assertThatThrownBy(() -> revision("0", "1.05")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void revisionAcceptsNegativeElevationBecauseTapCanSitBelowTheKeg() {
        var revision = new ServiceLine.Revision(UUID.randomUUID(), UUID.randomUUID(), BREWERY, 1, "vinil",
                new BigDecimal("4.8"), new BigDecimal("1.2"), new BigDecimal("1.05"), new BigDecimal("0.81"),
                new BigDecimal("-0.4"), new BigDecimal("0.069"), BigDecimal.ONE, new BigDecimal("4"),
                new BigDecimal("2.5"), METHOD, "1.0", null, ACTOR, AT);

        assertThat(revision.elevationMeters()).isEqualByComparingTo("-0.4");
    }

    private static ServiceLine.Revision revision(String applied, String recommended) {
        return new ServiceLine.Revision(UUID.randomUUID(), UUID.randomUUID(), BREWERY, 1, "vinil",
                new BigDecimal("4.8"), new BigDecimal(applied), new BigDecimal(recommended),
                new BigDecimal("0.81"), new BigDecimal("0.305"), new BigDecimal("0.069"), BigDecimal.ONE,
                new BigDecimal("4"), new BigDecimal("2.5"), METHOD, "1.0", null, ACTOR, AT);
    }
}
