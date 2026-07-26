package br.com.brew.brassia.planning.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.brew.brassia.planning.domain.MaterialExplosion.Component;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MaterialExplosionTest {

    private static final UUID GRAIN = UUID.randomUUID();
    private static final UUID HOP = UUID.randomUUID();

    @Test
    void scalesByVolume() {
        var lines = MaterialExplosion.explode(
                List.of(new Component(GRAIN, new BigDecimal("5"), "KG")),
                new BigDecimal("20"), new BigDecimal("40"), BigDecimal.ZERO);

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).ingredientId()).isEqualTo(GRAIN);
        assertThat(lines.get(0).requiredQuantity()).isEqualByComparingTo("10.0000");
        assertThat(lines.get(0).unit()).isEqualTo("KG");
    }

    @Test
    void appliesLossPercent() {
        var lines = MaterialExplosion.explode(
                List.of(new Component(GRAIN, new BigDecimal("10"), "KG")),
                new BigDecimal("20"), new BigDecimal("20"), new BigDecimal("10"));

        assertThat(lines.get(0).requiredQuantity()).isEqualByComparingTo("11.0000");
    }

    @Test
    void convertsUnitsToCanonical() {
        var lines = MaterialExplosion.explode(
                List.of(new Component(HOP, new BigDecimal("500"), "G")),
                new BigDecimal("20"), new BigDecimal("20"), BigDecimal.ZERO);

        assertThat(lines.get(0).requiredQuantity()).isEqualByComparingTo("0.5000");
        assertThat(lines.get(0).unit()).isEqualTo("KG");
    }

    @Test
    void aggregatesSameIngredientAcrossItemsAndUnits() {
        var lines = MaterialExplosion.explode(
                List.of(new Component(GRAIN, new BigDecimal("2"), "KG"),
                        new Component(GRAIN, new BigDecimal("500"), "G")),
                new BigDecimal("20"), new BigDecimal("20"), BigDecimal.ZERO);

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).requiredQuantity()).isEqualByComparingTo("2.5000");
    }

    @Test
    void rejectsUnknownUnit() {
        assertThatThrownBy(() -> MaterialExplosion.explode(
                List.of(new Component(GRAIN, new BigDecimal("1"), "SACK")),
                new BigDecimal("20"), new BigDecimal("20"), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidVolumesAndNegativeLoss() {
        var item = List.of(new Component(GRAIN, new BigDecimal("1"), "KG"));
        assertThatThrownBy(() -> MaterialExplosion.explode(item, BigDecimal.ZERO, new BigDecimal("20"), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MaterialExplosion.explode(item, new BigDecimal("20"), BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MaterialExplosion.explode(item, new BigDecimal("20"), new BigDecimal("20"),
                new BigDecimal("-1"))).isInstanceOf(IllegalArgumentException.class);
    }
}
