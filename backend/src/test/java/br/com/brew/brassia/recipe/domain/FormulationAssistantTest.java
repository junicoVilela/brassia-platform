package br.com.brew.brassia.recipe.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FormulationAssistantTest {

    private final FormulationAssistant assistant = new FormulationAssistant();

    private static AttributeRange range(String min, String max, String unit) {
        return new AttributeRange(new BigDecimal(min), new BigDecimal(max), unit);
    }

    @Test
    void guidesEachAttributeAgainstItsRange() {
        Map<String, BigDecimal> targets = new LinkedHashMap<>();
        targets.put("OG", new BigDecimal("1.060"));
        targets.put("IBU", new BigDecimal("90"));
        targets.put("COLOR", new BigDecimal("4"));
        Map<String, AttributeRange> ranges = new LinkedHashMap<>();
        ranges.put("OG", range("1.056", "1.070", "SG"));
        ranges.put("IBU", range("40", "70", "IBU"));
        ranges.put("COLOR", range("6", "14", "EBC"));

        var guidance = assistant.assess(targets, ranges);

        assertThat(guidance).filteredOn(g -> g.attribute().equals("OG")).singleElement()
                .satisfies(g -> {
                    assertThat(g.status()).isEqualTo(GuidanceStatus.WITHIN);
                    assertThat(g.suggestion()).isNull();
                });
        assertThat(guidance).filteredOn(g -> g.attribute().equals("IBU")).singleElement()
                .satisfies(g -> {
                    assertThat(g.status()).isEqualTo(GuidanceStatus.ABOVE);
                    assertThat(g.suggestion()).contains("lúpulo");
                });
        assertThat(guidance).filteredOn(g -> g.attribute().equals("COLOR")).singleElement()
                .satisfies(g -> {
                    assertThat(g.status()).isEqualTo(GuidanceStatus.BELOW);
                    assertThat(g.suggestion()).contains("escuro");
                });
    }

    @Test
    void attributeWithoutRangeIsReportedAsNoRange() {
        var guidance = assistant.assess(Map.of("ABV", new BigDecimal("6")), Map.of());
        assertThat(guidance).singleElement().satisfies(g -> {
            assertThat(g.status()).isEqualTo(GuidanceStatus.NO_RANGE);
            assertThat(g.suggestion()).isNull();
        });
    }

    @Test
    void customProfileWorksWithoutOfficialStyle() {
        // Faixas arbitrárias (perfil personalizado) — funciona sem padrão oficial.
        var guidance = assistant.assess(Map.of("ABV", new BigDecimal("4.0")),
                Map.of("ABV", range("5.0", "7.0", "%")));
        assertThat(guidance).singleElement().satisfies(g -> {
            assertThat(g.status()).isEqualTo(GuidanceStatus.BELOW);
            assertThat(g.suggestion()).contains("ABV");
        });
    }
}
