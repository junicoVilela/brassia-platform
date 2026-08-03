package br.com.brew.brassia.metrology.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CorrectionCurveTest {

    private static CurvePoint p(String reference, String measured) {
        return new CurvePoint(new BigDecimal(reference), new BigDecimal(measured));
    }

    /** Instrumento que lê sistematicamente 0,5 acima do padrão. */
    private static CorrectionCurve curva() {
        return CorrectionCurve.of(List.of(p("0", "0.5"), p("50", "50.5"), p("100", "100.5")));
    }

    @Test
    void corrigeNosPontosExatosDoCertificado() {
        var c = curva();

        assertThat(c.correct(new BigDecimal("0.5")).doubleValue()).isCloseTo(0.0, offset(1e-9));
        assertThat(c.correct(new BigDecimal("50.5")).doubleValue()).isCloseTo(50.0, offset(1e-9));
        assertThat(c.correct(new BigDecimal("100.5")).doubleValue()).isCloseTo(100.0, offset(1e-9));
    }

    @Test
    void interpolaLinearmenteEntreDoisPontos() {
        // Entre 0,5 → 0 e 50,5 → 50: uma leitura de 25,5 corresponde a 25 verdadeiros.
        assertThat(curva().correct(new BigDecimal("25.5")).doubleValue()).isCloseTo(25.0, offset(1e-9));
    }

    @Test
    void interpolaComDesvioQueMudaAoLongoDaFaixa() {
        // Desvio de +1 no início e +3 no fim: a correção acompanha o trecho, não uma média.
        var c = CorrectionCurve.of(List.of(p("0", "1"), p("100", "103")));

        assertThat(c.correct(new BigDecimal("52")).doubleValue()).isCloseTo(50.0, offset(1e-9));
    }

    @Test
    void recusaLeituraForaDaFaixaVerificadaEmVezDeExtrapolar() {
        var c = curva();

        assertThatThrownBy(() -> c.correct(new BigDecimal("120")))
                .isInstanceOf(OutsideCurveRangeException.class)
                .satisfies(e -> {
                    var ex = (OutsideCurveRangeException) e;
                    assertThat(ex.curveMin()).isEqualByComparingTo("0.5");
                    assertThat(ex.curveMax()).isEqualByComparingTo("100.5");
                });
        assertThatThrownBy(() -> c.correct(new BigDecimal("0.4")))
                .isInstanceOf(OutsideCurveRangeException.class);
    }

    @Test
    void aceitaExatamenteOsLimitesDaFaixa() {
        var c = curva();

        assertThat(c.correct(c.min())).isNotNull();
        assertThat(c.correct(c.max())).isNotNull();
    }

    @Test
    void exigeAoMenosDoisPontos() {
        assertThatThrownBy(() -> CorrectionCurve.of(List.of(p("0", "0.5"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dois pontos");
    }

    @Test
    void recusaCurvaComLeiturasRepetidas() {
        assertThatThrownBy(() -> CorrectionCurve.of(List.of(p("0", "10"), p("50", "10"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duas leituras iguais");
    }

    @Test
    void recusaCurvaNaoMonotona() {
        // Leitura maior correspondendo a referência menor: a mesma indicação teria dois valores
        // verdadeiros possíveis, e a correção viraria adivinhação.
        assertThatThrownBy(() -> CorrectionCurve.of(List.of(p("50", "10"), p("0", "20"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não é monótona");
    }

    @Test
    void ordenaOsPontosIndependenteDaOrdemInformada() {
        var c = CorrectionCurve.of(List.of(p("100", "100.5"), p("0", "0.5"), p("50", "50.5")));

        assertThat(c.min()).isEqualByComparingTo("0.5");
        assertThat(c.max()).isEqualByComparingTo("100.5");
        assertThat(c.points()).hasSize(3);
    }

    @Test
    void descreveOQueFoiAplicado() {
        assertThat(curva().method()).contains("interpolação linear", "3 pontos");
    }
}
