package br.com.brew.brassia.calculator.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                        "ibu-tinseth");
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
