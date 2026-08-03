package br.com.brew.brassia.metrology.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MeasurementRangeTest {

    private static MeasurementRange range(String min, String max, String resolution, String accuracy) {
        return new MeasurementRange(new BigDecimal(min), new BigDecimal(max), new BigDecimal(resolution),
                new BigDecimal(accuracy), "°C");
    }

    @Test
    void aceitaFaixaCoerente() {
        var r = range("-10", "110", "0.1", "0.5");

        assertThat(r.amplitude()).isEqualByComparingTo("120");
        assertThat(r.unit()).isEqualTo("°C");
    }

    @Test
    void recusaMinimoMaiorOuIgualAoMaximo() {
        assertThatThrownBy(() -> range("100", "100", "0.1", "0.5"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("menor que o máximo");
        assertThatThrownBy(() -> range("120", "100", "0.1", "0.5"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recusaResolucaoEPrecisaoNaoPositivas() {
        assertThatThrownBy(() -> range("0", "100", "0", "0.5")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> range("0", "100", "-0.1", "0.5")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> range("0", "100", "0.1", "0")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recusaResolucaoMaiorQueAAmplitude() {
        // Resolução de 5 numa faixa de 0 a 2: o instrumento não distingue nada dentro da própria faixa.
        assertThatThrownBy(() -> range("0", "2", "5", "0.5"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resolução não pode ser maior");
    }

    @Test
    void recusaPrecisaoMaiorQueAAmplitude() {
        assertThatThrownBy(() -> range("0", "2", "0.1", "5"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("precisão não pode ser maior");
    }

    @Test
    void aceitaResolucaoIgualAAmplitude() {
        // Limite: instrumento de dois estados (0 ou 100) é pobre, mas não é incoerente.
        assertThat(range("0", "100", "100", "1").amplitude()).isEqualByComparingTo("100");
    }

    @Test
    void exigeUnidade() {
        assertThatThrownBy(() -> new MeasurementRange(BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ONE,
                BigDecimal.ONE, " ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cobreExtremosInclusive() {
        var r = range("0", "100", "0.1", "0.5");

        assertThat(r.covers(new BigDecimal("0"))).isTrue();
        assertThat(r.covers(new BigDecimal("100"))).isTrue();
        assertThat(r.covers(new BigDecimal("100.1"))).isFalse();
        assertThat(r.covers(new BigDecimal("-0.1"))).isFalse();
    }
}
