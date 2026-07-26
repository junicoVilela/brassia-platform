package br.com.brew.brassia.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class StockUnitTest {

    @Test
    void convertsMassToCanonicalKg() {
        assertThat(StockUnit.G.toCanonical(new BigDecimal("500"))).isEqualByComparingTo("0.5");
        assertThat(StockUnit.KG.toCanonical(new BigDecimal("2"))).isEqualByComparingTo("2");
    }

    @Test
    void convertsFromCanonicalBackToUnit() {
        assertThat(StockUnit.G.fromCanonical(new BigDecimal("0.5"))).isEqualByComparingTo("500");
    }

    @Test
    void dimensionsMatchWithinFamily() {
        assertThat(StockUnit.KG.sameDimension(StockUnit.G)).isTrue();
        assertThat(StockUnit.L.sameDimension(StockUnit.ML)).isTrue();
        assertThat(StockUnit.KG.sameDimension(StockUnit.L)).isFalse();
        assertThat(StockUnit.UNIT.sameDimension(StockUnit.KG)).isFalse();
    }
}
