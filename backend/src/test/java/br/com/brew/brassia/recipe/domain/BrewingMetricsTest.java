package br.com.brew.brassia.recipe.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class BrewingMetricsTest {

    @Test
    void computesGravityFgAbvExactly() {
        // 10 kg a 1.040 → 400 pts/kg×10 = 400 pts brutos; ×100% ÷ 100 L = 4.0 pts... use números redondos:
        // 10 kg × (1.040-1)×1000 = 400 pontos; eficiência 100%; volume 100 L → OG pts = 4.0.
        var r = BrewingMetrics.compute(new BigDecimal("100"), BigDecimal.ONE,
                List.of(new BrewingMetrics.Fermentable(new BigDecimal("10"), new BigDecimal("1.040"), null)),
                List.of(), new BigDecimal("75"));

        assertThat(r.ogPoints()).isEqualByComparingTo("4.0");
        assertThat(r.ogSg()).isEqualByComparingTo("1.004");
        // FG = OG pts × (1 - 0.75) = 1.0 → FG 1.001
        assertThat(r.fgPoints()).isEqualByComparingTo("1.0");
        assertThat(r.attenuationPercent()).isEqualByComparingTo("75.0");
        assertThat(r.method()).isEqualTo("TINSETH_MOREY");
        assertThat(r.version()).isEqualTo(1);
    }

    @Test
    void efficiencyReducesGravity() {
        var full = BrewingMetrics.compute(new BigDecimal("100"), BigDecimal.ONE,
                List.of(new BrewingMetrics.Fermentable(new BigDecimal("10"), new BigDecimal("1.040"), null)),
                List.of(), null);
        var partial = BrewingMetrics.compute(new BigDecimal("100"), new BigDecimal("0.75"),
                List.of(new BrewingMetrics.Fermentable(new BigDecimal("10"), new BigDecimal("1.040"), null)),
                List.of(), null);
        assertThat(partial.ogPoints()).isEqualByComparingTo("3.0"); // 4.0 × 0.75
        assertThat(partial.ogPoints()).isLessThan(full.ogPoints());
    }

    @Test
    void computesPositiveIbuFromHops() {
        var r = BrewingMetrics.compute(new BigDecimal("400"), BigDecimal.ONE,
                List.of(new BrewingMetrics.Fermentable(new BigDecimal("20"), new BigDecimal("1.037"),
                        new BigDecimal("4"))),
                List.of(new BrewingMetrics.Hop(new BigDecimal("60"), new BigDecimal("12"), 60)),
                new BigDecimal("78"));
        assertThat(r.ibu()).isGreaterThan(BigDecimal.ZERO);
        assertThat(r.colorEbc()).isGreaterThan(BigDecimal.ZERO);
        assertThat(r.abv()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void noFermentablesYieldWaterGravity() {
        var r = BrewingMetrics.compute(new BigDecimal("100"), BigDecimal.ONE, List.of(), List.of(), null);
        assertThat(r.ogSg()).isEqualByComparingTo("1.0000");
        assertThat(r.abv()).isEqualByComparingTo("0.00");
        assertThat(r.colorEbc()).isEqualByComparingTo("0.0");
    }
}
