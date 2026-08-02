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
