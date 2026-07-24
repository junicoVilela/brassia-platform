package br.com.brew.brassia.referencedata.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class StyleTest {

    private static StyleRange range(String min, String max, String unit) {
        return new StyleRange(min == null ? null : new BigDecimal(min), max == null ? null : new BigDecimal(max), unit);
    }

    private static Style style(PermissionStatus permission) {
        return Style.create("21A", "American IPA", "IPA", "21", range("1.056", "1.070", "SG"),
                range("1.008", "1.014", "SG"), range("5.5", "7.5", "%"), range("40", "70", "IBU"),
                range("6", "14", "SRM"), "IPA lupulada e seca", "Aroma intenso de lúpulo...", permission);
    }

    @Test
    void keepsDetailedProfileOnlyWithFullPermission() {
        assertThat(style(PermissionStatus.GRANTED).detailedProfile()).isNotNull();
        assertThat(style(PermissionStatus.LIMITED_PERMISSION).detailedProfile()).isNull();
        // Impressão geral é sempre permitida.
        assertThat(style(PermissionStatus.LIMITED_PERMISSION).generalImpression()).isEqualTo("IPA lupulada e seca");
    }

    @Test
    void evaluatesOnlyPresentValuesAndRanges() {
        var checks = style(PermissionStatus.GRANTED)
                .evaluate(new BigDecimal("1.060"), null, new BigDecimal("6.2"), new BigDecimal("90"),
                        new BigDecimal("10"));

        // FG ausente não gera check; OG/ABV/COLOR dentro; IBU fora.
        assertThat(checks).extracting(RangeCheck::metric).containsExactlyInAnyOrder("OG", "ABV", "IBU", "COLOR");
        assertThat(checks).filteredOn(c -> c.metric().equals("OG")).singleElement()
                .extracting(RangeCheck::withinRange).isEqualTo(true);
        assertThat(checks).filteredOn(c -> c.metric().equals("IBU")).singleElement()
                .extracting(RangeCheck::withinRange).isEqualTo(false);
    }

    @Test
    void emptyRangeAndNullValueAreSkipped() {
        var minimal = Style.create("X1", "Livre", null, null, StyleRange.none(), StyleRange.none(),
                StyleRange.none(), StyleRange.none(), StyleRange.none(), null, null, PermissionStatus.GRANTED);
        assertThat(minimal.evaluate(new BigDecimal("1.05"), null, null, null, null)).isEmpty();
    }
}
