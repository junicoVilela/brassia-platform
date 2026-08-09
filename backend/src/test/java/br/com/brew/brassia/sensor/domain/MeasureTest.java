package br.com.brew.brassia.sensor.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A grandeza medida e a faixa que a torna plausível (INT-001). */
class MeasureTest {

    @Test
    @DisplayName("unidade é normalizada para maiúscula e sem espaço")
    void normalizaUnidade() {
        assertThat(Measure.TEMPERATURE.requireUnit(" c ")).isEqualTo("C");
        assertThat(Measure.DENSITY.requireUnit("sg")).isEqualTo("SG");
        assertThat(Measure.FLOW.requireUnit("l_min")).isEqualTo("L_MIN");
    }

    @Test
    @DisplayName("unidade de outra grandeza é recusada: PSI não mede temperatura")
    void recusaUnidadeDeOutraGrandeza() {
        assertThatThrownBy(() -> Measure.TEMPERATURE.requireUnit("PSI"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("incompatível");
    }

    @Test
    @DisplayName("unidade ausente é recusada em vez de assumida")
    void recusaUnidadeAusente() {
        // Assumir a primeira unidade da lista seria escolher a escala por sorteio: um valor "20" sem
        // unidade é 20 °C ou 20 °F conforme o palpite, e a série ficaria com as duas misturadas.
        assertThatThrownBy(() -> Measure.TEMPERATURE.requireUnit(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Measure.TEMPERATURE.requireUnit("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("valor dentro da faixa é plausível; fora não é")
    void faixaDeplausibilidade() {
        assertThat(Measure.TEMPERATURE.isImplausible(new BigDecimal("18.5"), "C")).isFalse();
        assertThat(Measure.TEMPERATURE.isImplausible(new BigDecimal("120"), "C")).isTrue();
        assertThat(Measure.DENSITY.isImplausible(new BigDecimal("1.048"), "SG")).isFalse();
        assertThat(Measure.DENSITY.isImplausible(new BigDecimal("4.2"), "SG")).isTrue();
    }

    @Test
    @DisplayName("a faixa é por unidade, não por grandeza: 60 é plausível em °F e não em °C")
    void faixaEPorUnidade() {
        // É o caso que justifica guardar a unidade junto do valor em vez de converter tudo na borda: 60 °F
        // é fermentação de ale, 60 °C é pasteurização — e nenhum número sozinho distingue os dois.
        assertThat(Measure.TEMPERATURE.isImplausible(new BigDecimal("60"), "F")).isFalse();
        assertThat(Measure.TEMPERATURE.isImplausible(new BigDecimal("60"), "C")).isTrue();
    }

    @Test
    @DisplayName("grandeza desconhecida é recusada com o nome recebido")
    void recusaGrandezaDesconhecida() {
        assertThatThrownBy(() -> Measure.of("PH"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PH");
    }
}
