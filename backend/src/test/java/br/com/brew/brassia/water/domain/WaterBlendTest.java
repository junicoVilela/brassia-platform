package br.com.brew.brassia.water.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class WaterBlendTest {

    private IonProfile ions(String calcium, String bicarbonate) {
        return new IonProfile(new BigDecimal(calcium), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal(bicarbonate));
    }

    @Test
    void blendsByVolumeWeightedAverage() {
        // 100 L a 40 mg/L + 100 L a 80 mg/L → 60 mg/L (balanço fecha).
        var result = WaterBlend.simulate(List.of(
                new WaterBlend.Component(ions("40", "100"), new BigDecimal("100")),
                new WaterBlend.Component(ions("80", "200"), new BigDecimal("100"))));

        assertThat(result.totalVolumeLiters()).isEqualByComparingTo("200");
        assertThat(result.ions().calcium()).isEqualByComparingTo("60.00");
        assertThat(result.ions().bicarbonate()).isEqualByComparingTo("150.00");
    }

    @Test
    void weightsByVolumeNotCount() {
        // 300 L a 40 + 100 L a 80 → (300*40 + 100*80)/400 = 50.
        var result = WaterBlend.simulate(List.of(
                new WaterBlend.Component(ions("40", "0"), new BigDecimal("300")),
                new WaterBlend.Component(ions("80", "0"), new BigDecimal("100"))));

        assertThat(result.ions().calcium()).isEqualByComparingTo("50.00");
    }

    @Test
    void rejectsEmptyOrNonPositiveVolume() {
        assertThatThrownBy(() -> WaterBlend.simulate(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WaterBlend.simulate(List.of(
                new WaterBlend.Component(ions("40", "0"), BigDecimal.ZERO))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positivo");
    }
}
