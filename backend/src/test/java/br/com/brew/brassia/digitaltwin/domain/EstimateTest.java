package br.com.brew.brassia.digitaltwin.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A estimativa aprendida do histórico (DTW-001).
 *
 * <p>O que estes testes fixam é o critério da história: <strong>poucos dados geram baixa confiança
 * explícita</strong>. O número sozinho é a parte perigosa — "eficiência de 74%" pode vir de trinta
 * brassagens agrupadas ou de duas, uma de 60 e uma de 88, e as duas significam coisas opostas para quem vai
 * planejar.
 */
class EstimateTest {

    private static List<BigDecimal> valores(String... valores) {
        return java.util.Arrays.stream(valores).map(BigDecimal::new).toList();
    }

    @Test
    @DisplayName("uma observação não estima nada — não é confiança baixa, é ausência de estimativa")
    void umaObservacaoNaoEstima() {
        // Com uma só não há desvio a calcular, e a "faixa" seria o próprio ponto: uma estimativa com
        // aparência de precisão absoluta sobre a menor evidência possível.
        var estimate = Estimate.from(valores("74"));

        assertThat(estimate.confidence()).isEqualTo(Confidence.INSUFFICIENT);
        assertThat(estimate.usable()).isFalse();
        assertThat(estimate.mean()).isNull();
        assertThat(estimate.sampleSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("nenhuma observação também não estima")
    void nenhumaObservacao() {
        assertThat(Estimate.from(List.of()).confidence()).isEqualTo(Confidence.INSUFFICIENT);
    }

    @Test
    @DisplayName("duas observações estimam, mas com confiança BAIXA declarada")
    void duasObservacoesConfiancaBaixa() {
        var estimate = Estimate.from(valores("60", "88"));

        assertThat(estimate.usable()).isTrue();
        assertThat(estimate.confidence()).isEqualTo(Confidence.LOW);
        assertThat(estimate.mean()).isEqualByComparingTo("74");
    }

    @Test
    @DisplayName("MESMA MÉDIA, EVIDÊNCIAS OPOSTAS: a faixa e a confiança separam as duas")
    void mesmaMediaEvidenciasOpostas() {
        // O ponto central da história. Duas brassagens (60 e 88) e trinta agrupadas em torno de 74 dão a
        // mesma média — e significam coisas completamente diferentes.
        var poucasEDispersas = Estimate.from(valores("60", "88"));
        var muitasEAgrupadas = Estimate.from(IntStream.range(0, 30)
                .mapToObj(i -> new BigDecimal(i % 2 == 0 ? "73" : "75"))
                .toList());

        assertThat(poucasEDispersas.mean()).isEqualByComparingTo(muitasEAgrupadas.mean());

        // A faixa denuncia: uma é larga, a outra é estreita.
        assertThat(poucasEDispersas.spread()).isGreaterThan(muitasEAgrupadas.spread());
        // E o rótulo diz em qual se pode apoiar.
        assertThat(poucasEDispersas.confidence()).isEqualTo(Confidence.LOW);
        assertThat(muitasEAgrupadas.confidence()).isEqualTo(Confidence.HIGH);
    }

    @Test
    @DisplayName("a confiança sobe com o tamanho da amostra")
    void confiancaSobeComAmostra() {
        assertThat(Estimate.from(constantes(2)).confidence()).isEqualTo(Confidence.LOW);
        assertThat(Estimate.from(constantes(4)).confidence()).isEqualTo(Confidence.LOW);
        assertThat(Estimate.from(constantes(5)).confidence()).isEqualTo(Confidence.MODERATE);
        assertThat(Estimate.from(constantes(9)).confidence()).isEqualTo(Confidence.MODERATE);
        assertThat(Estimate.from(constantes(10)).confidence()).isEqualTo(Confidence.HIGH);
    }

    @Test
    @DisplayName("A FAIXA ENCOLHE com mais observações da mesma dispersão")
    void faixaEncolheComAmostra() {
        // É o intervalo da MÉDIA, não a variação observada: ele diz quanto ainda não se sabe, e é isso que
        // encolhe conforme se acumula histórico.
        var poucas = Estimate.from(valores("70", "78", "74", "72"));
        var muitas = Estimate.from(valores("70", "78", "74", "72", "70", "78", "74", "72",
                "70", "78", "74", "72", "70", "78", "74", "72"));

        // Mesma dispersão entre brassagens…
        assertThat(muitas.standardDeviation()).isCloseTo(poucas.standardDeviation(),
                org.assertj.core.data.Offset.offset(new BigDecimal("0.5")));
        // …e muito menos incerteza sobre a média.
        assertThat(muitas.spread()).isLessThan(poucas.spread());
    }

    @Test
    @DisplayName("observações idênticas dão desvio zero e faixa de largura zero")
    void observacoesIdenticas() {
        // Legítimo e raro: o processo repetiu exatamente. A faixa nula aqui é honesta — não há variação
        // observada — e a confiança continua sendo governada pelo tamanho da amostra.
        var estimate = Estimate.from(constantes(12));

        assertThat(estimate.standardDeviation()).isEqualByComparingTo("0");
        assertThat(estimate.spread()).isEqualByComparingTo("0");
        assertThat(estimate.confidence()).isEqualTo(Confidence.HIGH);
    }

    @Test
    @DisplayName("a média fica dentro da própria faixa")
    void mediaDentroDaFaixa() {
        var estimate = Estimate.from(valores("68", "71", "74", "77", "80"));

        assertThat(estimate.mean()).isBetween(estimate.lowerBound(), estimate.upperBound());
    }

    @Test
    @DisplayName("o desvio é amostral: uma amostra não é o universo")
    void desvioAmostral() {
        // Com n-1 sobre [70, 80]: desvio ~7,07. Com n seria ~5,0 — e subestimar a dispersão é o oposto do
        // que uma estimativa honesta deve fazer.
        var estimate = Estimate.from(valores("70", "80"));

        assertThat(estimate.standardDeviation()).isCloseTo(new BigDecimal("7.0711"),
                org.assertj.core.data.Offset.offset(new BigDecimal("0.001")));
    }

    @Test
    @DisplayName("valores decimais são preservados com quatro casas")
    void quatroCasas() {
        // Suficiente para densidade (1,0483); mais que isso exibiria exatidão que o refratômetro não tem.
        var estimate = Estimate.from(valores("1.048", "1.050", "1.046", "1.052"));

        assertThat(estimate.mean()).isEqualByComparingTo("1.0490");
        assertThat(estimate.mean().scale()).isEqualTo(4);
    }

    @Test
    @DisplayName("estimativa insuficiente não tem faixa — nem larga, nem estreita")
    void insuficienteNaoTemFaixa() {
        var estimate = Estimate.from(valores("74"));

        assertThat(estimate.spread()).isNull();
        assertThat(estimate.lowerBound()).isNull();
        assertThat(estimate.upperBound()).isNull();
    }

    private static List<BigDecimal> constantes(int n) {
        return IntStream.range(0, n).mapToObj(i -> new BigDecimal("74")).toList();
    }
}
