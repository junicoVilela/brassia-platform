package br.com.brew.brassia.quality.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SpecLimitsTest {

    private static BigDecimal v(String value) {
        return new BigDecimal(value);
    }

    @Test
    void aceitaFaixaComOsDoisLimites() {
        var l = new SpecLimits(v("4.5"), v("5.5"), v("5.0"), "pH");

        assertThat(l.conforms(v("5.0"))).isTrue();
        assertThat(l.describe()).isEqualTo("4.5 a 5.5 pH");
    }

    @Test
    void aceitaLimiteSoDeTeto() {
        // "O₂ ≤ 50 ppb" é especificação real; exigir um piso obrigaria a inventá-lo.
        var l = new SpecLimits(null, v("50"), null, "ppb");

        assertThat(l.conforms(v("0"))).isTrue();
        assertThat(l.conforms(v("50"))).isTrue();
        assertThat(l.conforms(v("50.1"))).isFalse();
        assertThat(l.describe()).isEqualTo("≤ 50 ppb");
    }

    @Test
    void aceitaLimiteSoDePiso() {
        var l = new SpecLimits(v("75"), null, null, "%");

        assertThat(l.conforms(v("75"))).isTrue();
        assertThat(l.conforms(v("74.9"))).isFalse();
        assertThat(l.describe()).isEqualTo("≥ 75 %");
    }

    @Test
    void osLimitesSaoInclusivos() {
        // O limite é o último valor aceitável, não o primeiro recusado.
        var l = new SpecLimits(v("4.5"), v("5.5"), null, "pH");

        assertThat(l.conforms(v("4.5"))).isTrue();
        assertThat(l.conforms(v("5.5"))).isTrue();
        assertThat(l.conforms(v("4.4999"))).isFalse();
        assertThat(l.conforms(v("5.5001"))).isFalse();
    }

    @Test
    void exigeAoMenosUmLimite() {
        assertThatThrownBy(() -> new SpecLimits(null, null, null, "pH"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ao menos um limite");
    }

    @Test
    void recusaMinimoMaiorOuIgualAoMaximo() {
        assertThatThrownBy(() -> new SpecLimits(v("5.5"), v("4.5"), null, "pH"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SpecLimits(v("5"), v("5"), null, "pH"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recusaAlvoForaDaFaixa() {
        assertThatThrownBy(() -> new SpecLimits(v("4.5"), v("5.5"), v("6"), "pH"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maior que o máximo");
        assertThatThrownBy(() -> new SpecLimits(v("4.5"), v("5.5"), v("4"), "pH"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("menor que o mínimo");
    }

    @Test
    void aViolacaoDizQualLadoFoiRompidoEPorQualLimite() {
        var l = new SpecLimits(v("4.5"), v("5.5"), null, "pH");

        var acima = l.violation(v("6")).orElseThrow();
        assertThat(acima.bound()).isEqualTo(SpecLimits.Bound.ABOVE_MAX);
        assertThat(acima.limit()).isEqualByComparingTo("5.5");

        var abaixo = l.violation(v("4")).orElseThrow();
        assertThat(abaixo.bound()).isEqualTo(SpecLimits.Bound.BELOW_MIN);
        assertThat(abaixo.limit()).isEqualByComparingTo("4.5");

        assertThat(l.violation(v("5"))).isEmpty();
    }

    @Test
    void aDescricaoNaoCarregaAEscalaDoBanco() {
        // O limite volta do NUMERIC(14,4) como "50.0000"; exibir assim sugere precisão inexistente.
        var l = new SpecLimits(null, new BigDecimal("50.0000"), null, "ppb");

        assertThat(l.describe()).isEqualTo("≤ 50 ppb");
    }

    @Test
    void exigeUnidade() {
        assertThatThrownBy(() -> new SpecLimits(v("1"), v("2"), null, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
