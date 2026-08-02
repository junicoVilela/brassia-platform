package br.com.brew.brassia.calculator.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CalculatorsTest {

    private final Calculators calculators = new Calculators();

    private static Map<String, BigDecimal> in(Object... kv) {
        var map = new java.util.LinkedHashMap<String, BigDecimal>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], new BigDecimal(kv[i + 1].toString()));
        }
        return map;
    }

    @Test
    void catalogListsAllCalculators() {
        assertThat(calculators.catalog()).extracting(CalculatorSpec::id)
                .contains("abv", "apparent-attenuation", "sg-to-plato", "srm-to-ebc", "celsius-to-fahrenheit",
                        "dilution-water", "concentration-boiloff", "hydrometer-temp-correction", "volume-topup",
                        "ibu-tinseth", "co2-residual", "priming-sugar", "forced-carbonation-pressure");
    }

    // --- carbonatação (PKG-002) ---

    @Test
    void co2ResidualGolden() {
        // Dataset dourado: valores de referência da tabela de CO₂ residual por temperatura.
        assertThat(calculators.compute("co2-residual", in("tempC", "20")).value().doubleValue())
                .isCloseTo(0.86, offset(0.03));
        assertThat(calculators.compute("co2-residual", in("tempC", "4")).value().doubleValue())
                .isCloseTo(1.48, offset(0.03));
        assertThat(calculators.compute("co2-residual", in("tempC", "18")).value().doubleValue())
                .isCloseTo(0.92, offset(0.03));
    }

    @Test
    void co2ResidualFallsAsTemperatureRises() {
        var cold = calculators.compute("co2-residual", in("tempC", "4")).value();
        var warm = calculators.compute("co2-residual", in("tempC", "22")).value();

        assertThat(cold).isGreaterThan(warm);
        assertThat(calculators.compute("co2-residual", in("tempC", "20")).unit()).isEqualTo("vol");
    }

    @Test
    void primingSugarGolden() {
        // 20 L, alvo 2.4 vol, residual 0.86 vol, sacarose (0.514 g CO₂/g):
        // (2.4 − 0.86) × 20 × 1.96 / 0.514 ≈ 117.4 g
        var r = calculators.compute("priming-sugar", in("targetVolumes", "2.4", "residualVolumes", "0.86",
                "beerVolumeLiters", "20", "sugarYield", "0.514"));

        assertThat(r.value().doubleValue()).isCloseTo(117.4, offset(0.5));
        assertThat(r.unit()).isEqualTo("g");
    }

    @Test
    void primingSugarUsesResidualInsteadOfTheWholeTarget() {
        var withResidual = calculators.compute("priming-sugar", in("targetVolumes", "2.4",
                "residualVolumes", "0.86", "beerVolumeLiters", "20", "sugarYield", "0.514")).value();
        var ignoringResidual = calculators.compute("priming-sugar", in("targetVolumes", "2.4",
                "residualVolumes", "0", "beerVolumeLiters", "20", "sugarYield", "0.514")).value();

        // Ignorar o residual pediria muito mais açúcar — é assim que se estoura a garrafa.
        assertThat(ignoringResidual).isGreaterThan(withResidual);
    }

    @Test
    void primingSugarWarnsWhenBeerAlreadyHasTheTarget() {
        var r = calculators.compute("priming-sugar", in("targetVolumes", "1.5", "residualVolumes", "2.0",
                "beerVolumeLiters", "20", "sugarYield", "0.514"));

        assertThat(r.value()).isEqualByComparingTo("0.0");
        assertThat(r.alerts()).anyMatch(a -> a.contains("sobrepressão"));
    }

    @Test
    void primingSugarRejectsNonPositiveYieldAndVolume() {
        assertThat(calculators.compute("priming-sugar", in("targetVolumes", "2.4", "residualVolumes", "0.86",
                "beerVolumeLiters", "20", "sugarYield", "0")).alerts()).isNotEmpty();
        assertThat(calculators.compute("priming-sugar", in("targetVolumes", "2.4", "residualVolumes", "0.86",
                "beerVolumeLiters", "0", "sugarYield", "0.514")).alerts()).isNotEmpty();
    }

    @Test
    void forcedCarbonationPressureGolden() {
        // Tabela de carbonatação: 2.5 vol a 4 °C ≈ 12 psi (0.81 bar); a 12 °C ≈ 20 psi (1.36 bar).
        assertThat(calculators.compute("forced-carbonation-pressure",
                in("targetVolumes", "2.5", "tempC", "4")).value().doubleValue())
                .isCloseTo(0.81, offset(0.05));
        assertThat(calculators.compute("forced-carbonation-pressure",
                in("targetVolumes", "2.5", "tempC", "12")).value().doubleValue())
                .isCloseTo(1.36, offset(0.05));
    }

    @Test
    void forcedCarbonationNeedsMorePressureWhenWarmer() {
        var cold = calculators.compute("forced-carbonation-pressure", in("targetVolumes", "2.5", "tempC", "2"))
                .value();
        var warm = calculators.compute("forced-carbonation-pressure", in("targetVolumes", "2.5", "tempC", "18"))
                .value();

        assertThat(warm).isGreaterThan(cold);
    }

    @Test
    void forcedCarbonationAlertsWhenNoPressureIsNeeded() {
        // Muito frio e alvo baixo: a cerveja já passa do alvo sem pressão aplicada.
        var r = calculators.compute("forced-carbonation-pressure", in("targetVolumes", "0.5", "tempC", "0"));

        assertThat(r.value()).isEqualByComparingTo("0.00");
        assertThat(r.alerts()).isNotEmpty();
    }

    // --- balanceamento de linha (GAS-002) ---

    @Test
    void beerColumnPressureGolden() {
        // ρ·g·h com cerveja a 1010 kg/m³: ~0,099 bar por metro de coluna.
        assertThat(calculators.compute("beer-column-pressure", in("elevationMeters", "1"))
                .value().doubleValue()).isCloseTo(0.0991, offset(0.0005));
        // Torneira abaixo do barril devolve pressão.
        assertThat(calculators.compute("beer-column-pressure", in("elevationMeters", "-0.5"))
                .value().doubleValue()).isCloseTo(-0.0495, offset(0.0005));
    }

    @Test
    void lineBalanceGolden() {
        // 0,81 bar (2,5 vol a 4 °C), torneira 1 pé acima (0,305 m), residual 0,069 bar (1 psi),
        // tubo 3/16" de vinil a 3 psi/pé = 0,679 bar/m → ~1,05 m, que é o 3,5 pé da regra clássica.
        var r = calculators.compute("line-balance", in(
                "appliedPressureBar", "0.81", "elevationMeters", "0.305", "residualPressureBar", "0.069",
                "resistanceBarPerMeter", "0.679", "targetFlowLpm", "1", "referenceFlowLpm", "1"));

        assertThat(r.value().doubleValue()).isCloseTo(1.05, offset(0.05));
        assertThat(r.unit()).isEqualTo("m");
    }

    @Test
    void widerTubingNeedsMoreLengthForTheSamePressure() {
        // 3/8" tem resistência muito menor, então precisa de muito mais linha para equilibrar.
        var narrow = calculators.compute("line-balance", in(
                "appliedPressureBar", "1.0", "elevationMeters", "0", "residualPressureBar", "0.069",
                "resistanceBarPerMeter", "0.679", "targetFlowLpm", "1", "referenceFlowLpm", "1")).value();
        var wide = calculators.compute("line-balance", in(
                "appliedPressureBar", "1.0", "elevationMeters", "0", "residualPressureBar", "0.069",
                "resistanceBarPerMeter", "0.045", "targetFlowLpm", "1", "referenceFlowLpm", "1")).value();

        assertThat(wide).isGreaterThan(narrow);
    }

    @Test
    void higherFlowScalesResistanceAndShortensTheLine() {
        var normal = calculators.compute("line-balance", in(
                "appliedPressureBar", "1.0", "elevationMeters", "0", "residualPressureBar", "0.069",
                "resistanceBarPerMeter", "0.679", "targetFlowLpm", "1", "referenceFlowLpm", "1")).value();
        var faster = calculators.compute("line-balance", in(
                "appliedPressureBar", "1.0", "elevationMeters", "0", "residualPressureBar", "0.069",
                "resistanceBarPerMeter", "0.679", "targetFlowLpm", "2", "referenceFlowLpm", "1")).value();

        // Escoamento laminar: dobrar a vazão dobra a resistência efetiva, então a linha cai à metade.
        assertThat(faster.doubleValue()).isCloseTo(normal.doubleValue() / 2, offset(0.02));
    }

    @Test
    void elevationEatsPressureAndCanMakeTheSetupImpossible() {
        var flat = calculators.compute("line-balance", in(
                "appliedPressureBar", "0.81", "elevationMeters", "0", "residualPressureBar", "0.069",
                "resistanceBarPerMeter", "0.679", "targetFlowLpm", "1", "referenceFlowLpm", "1")).value();
        var uphill = calculators.compute("line-balance", in(
                "appliedPressureBar", "0.81", "elevationMeters", "3", "residualPressureBar", "0.069",
                "resistanceBarPerMeter", "0.679", "targetFlowLpm", "1", "referenceFlowLpm", "1"));

        assertThat(uphill.value()).isLessThan(flat);
        // 3 m de subida consomem ~0,30 bar; ainda sobra pressão, mas a linha encurta muito.
        assertThat(uphill.value().doubleValue()).isGreaterThan(0);

        // 8 m de subida consomem mais do que a pressão aplicada: a cerveja não sobe.
        var impossible = calculators.compute("line-balance", in(
                "appliedPressureBar", "0.81", "elevationMeters", "8", "residualPressureBar", "0.069",
                "resistanceBarPerMeter", "0.679", "targetFlowLpm", "1", "referenceFlowLpm", "1"));
        assertThat(impossible.value()).isEqualByComparingTo("0");
        assertThat(impossible.alerts()).anyMatch(a -> a.contains("não flui"));
    }

    @Test
    void tapBelowTheKegGainsPressureAndNeedsMoreLine() {
        var level = calculators.compute("line-balance", in(
                "appliedPressureBar", "0.81", "elevationMeters", "0", "residualPressureBar", "0.069",
                "resistanceBarPerMeter", "0.679", "targetFlowLpm", "1", "referenceFlowLpm", "1")).value();
        var below = calculators.compute("line-balance", in(
                "appliedPressureBar", "0.81", "elevationMeters", "-1", "residualPressureBar", "0.069",
                "resistanceBarPerMeter", "0.679", "targetFlowLpm", "1", "referenceFlowLpm", "1")).value();

        assertThat(below).isGreaterThan(level);
    }

    @Test
    void lineBalanceRejectsNonPositiveResistanceAndFlow() {
        assertThat(calculators.compute("line-balance", in(
                "appliedPressureBar", "1.0", "elevationMeters", "0", "residualPressureBar", "0.069",
                "resistanceBarPerMeter", "0", "targetFlowLpm", "1", "referenceFlowLpm", "1")).alerts())
                .isNotEmpty();
        assertThat(calculators.compute("line-balance", in(
                "appliedPressureBar", "1.0", "elevationMeters", "0", "residualPressureBar", "0.069",
                "resistanceBarPerMeter", "0.679", "targetFlowLpm", "0", "referenceFlowLpm", "1")).alerts())
                .isNotEmpty();
    }

    @Test
    void carbonationResultsCarryMethodAndVersion() {
        var r = calculators.compute("forced-carbonation-pressure", in("targetVolumes", "2.5", "tempC", "4"));

        assertThat(r.method()).isNotBlank();
        assertThat(r.version()).isNotBlank();
        assertThat(r.assumptions()).isNotEmpty();
    }

    @Test
    void concentrationBoilOffGolden() {
        // OG 1.040 (40 pts) em 20 L → 1.050 (50 pts): volume final 16 L, evaporar 4 L.
        var r = calculators.compute("concentration-boiloff",
                in("currentOg", "1.040", "currentVolume", "20", "targetOg", "1.050"));
        assertThat(r.value()).isEqualByComparingTo("4.000");
        assertThat(r.unit()).isEqualTo("L");
    }

    @Test
    void concentrationAlertsWhenTargetNotHigher() {
        var r = calculators.compute("concentration-boiloff",
                in("currentOg", "1.050", "currentVolume", "20", "targetOg", "1.040"));
        assertThat(r.value()).isEqualByComparingTo("0");
        assertThat(r.alerts()).isNotEmpty();
    }

    @Test
    void hydrometerTempCorrectionRaisesReadingWhenSampleHotter() {
        // Amostra quente lida abaixo do real → correção sobe a densidade.
        var r = calculators.compute("hydrometer-temp-correction",
                in("measuredSg", "1.050", "sampleTempC", "30", "calibrationTempC", "20"));
        assertThat(r.unit()).isEqualTo("SG");
        assertThat(r.value()).isGreaterThan(new BigDecimal("1.050"));
    }

    @Test
    void volumeTopUpGolden() {
        var r = calculators.compute("volume-topup", in("currentVolume", "18", "targetVolume", "20"));
        assertThat(r.value()).isEqualByComparingTo("2.000");
        assertThat(r.unit()).isEqualTo("L");
    }

    @Test
    void abvGolden() {
        var r = calculators.compute("abv", in("og", "1.050", "fg", "1.010"));
        assertThat(r.value()).isEqualByComparingTo("5.25");
        assertThat(r.unit()).isEqualTo("%");
        assertThat(r.version()).isEqualTo("1.0");
    }

    @Test
    void abvAlertsWhenFgAboveOg() {
        var r = calculators.compute("abv", in("og", "1.010", "fg", "1.050"));
        assertThat(r.alerts()).isNotEmpty();
    }

    @Test
    void attenuationGolden() {
        var r = calculators.compute("apparent-attenuation", in("og", "1.050", "fg", "1.010"));
        assertThat(r.value()).isEqualByComparingTo("80.00");
    }

    @Test
    void sgToPlatoGolden() {
        var r = calculators.compute("sg-to-plato", in("sg", "1.040"));
        assertThat(r.value().doubleValue()).isCloseTo(9.99, org.assertj.core.data.Offset.offset(0.05));
    }

    @Test
    void srmToEbcGolden() {
        var r = calculators.compute("srm-to-ebc", in("srm", "10"));
        assertThat(r.value()).isEqualByComparingTo("19.70");
    }

    @Test
    void celsiusToFahrenheitGolden() {
        var r = calculators.compute("celsius-to-fahrenheit", in("celsius", "20"));
        assertThat(r.value()).isEqualByComparingTo("68.00");
    }

    @Test
    void dilutionGolden() {
        var r = calculators.compute("dilution-water", in("currentOg", "1.060", "currentVolume", "20", "targetOg",
                "1.050"));
        assertThat(r.value()).isEqualByComparingTo("4.000");
    }

    @Test
    void dilutionAlertsWhenTargetNotLower() {
        var r = calculators.compute("dilution-water", in("currentOg", "1.050", "currentVolume", "20", "targetOg",
                "1.060"));
        assertThat(r.value()).isEqualByComparingTo("0");
        assertThat(r.alerts()).isNotEmpty();
    }

    @Test
    void ibuTinsethGolden() {
        var r = calculators.compute("ibu-tinseth", in("alphaAcid", "5", "weightGrams", "30", "timeMinutes", "60",
                "volumeLiters", "20", "boilGravity", "1.050"));
        assertThat(r.value().doubleValue()).isCloseTo(17.3, org.assertj.core.data.Offset.offset(1.0));
    }

    @Test
    void unknownCalculatorRejected() {
        assertThatThrownBy(() -> calculators.compute("nope", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingInputRejected() {
        assertThatThrownBy(() -> calculators.compute("abv", in("og", "1.050")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fg");
    }
}
