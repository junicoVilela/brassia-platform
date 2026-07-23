package br.com.brew.brassia.recipe.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class VolumeBalanceTest {

    @Test
    void goldenDataset() {
        // 400 L final; 20 kg grão (absorção 1 L/kg = 20); 60 min a 8 L/h = 8; dead space 20.
        var r = VolumeBalance.compute(new BigDecimal("400"), new BigDecimal("20"), 60,
                new BigDecimal("20"), new BigDecimal("8"));

        assertThat(r.finalVolumeLiters()).isEqualByComparingTo("400.00");
        assertThat(r.grainAbsorptionLiters()).isEqualByComparingTo("20.00");
        assertThat(r.evaporationLiters()).isEqualByComparingTo("8.00");
        assertThat(r.lossesLiters()).isEqualByComparingTo("20.00");
        assertThat(r.preBoilVolumeLiters()).isEqualByComparingTo("408.00");
        assertThat(r.totalWaterLiters()).isEqualByComparingTo("448.00");
        assertThat(r.method()).isEqualTo("GRAIN_ABSORPTION_BOILOFF_V1");
    }

    @Test
    void balanceCloses() {
        var r = VolumeBalance.compute(new BigDecimal("350"), new BigDecimal("18"), 90,
                new BigDecimal("15"), new BigDecimal("10"));
        var sumOfParcels = r.finalVolumeLiters()
                .add(r.grainAbsorptionLiters())
                .add(r.evaporationLiters())
                .add(r.lossesLiters());
        assertThat(sumOfParcels).isEqualByComparingTo(r.totalWaterLiters());
    }

    @Test
    void nullBoilTimeMeansNoEvaporation() {
        var r = VolumeBalance.compute(new BigDecimal("200"), new BigDecimal("10"), null,
                new BigDecimal("5"), new BigDecimal("8"));
        assertThat(r.evaporationLiters()).isEqualByComparingTo("0.00");
        assertThat(r.totalWaterLiters()).isEqualByComparingTo("215.00");
    }

    @Test
    void rejectsNegativeInputs() {
        assertThatThrownBy(() -> VolumeBalance.compute(new BigDecimal("-1"), BigDecimal.ONE, 60,
                BigDecimal.ONE, BigDecimal.ONE)).isInstanceOf(IllegalArgumentException.class);
    }
}
