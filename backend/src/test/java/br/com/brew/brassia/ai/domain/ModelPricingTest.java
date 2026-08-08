package br.com.brew.brassia.ai.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O preço de uma chamada (AIA-001).
 *
 * <p>O ponto destes testes é a precisão: uma chamada custa frações de centavo, e o arredondamento errado
 * transformaria mil chamadas em zero reais.
 */
class ModelPricingTest {

    /** Preço de referência do Claude Opus 5: US$ 5 por milhão de entrada, US$ 25 de saída. */
    private static final ModelPricing OPUS = new ModelPricing(
            new BigDecimal("5.00"), new BigDecimal("25.00"), "USD");

    @Test
    @DisplayName("entrada e saída são cobradas a preços diferentes")
    void entradaESaidaTemPrecosProprios() {
        // 1M de entrada = 5,00; 1M de saída = 25,00.
        assertThat(OPUS.costOf(new TokenUsage(1_000_000, 0))).isEqualByComparingTo("5.00");
        assertThat(OPUS.costOf(new TokenUsage(0, 1_000_000))).isEqualByComparingTo("25.00");
        assertThat(OPUS.costOf(new TokenUsage(1_000_000, 1_000_000))).isEqualByComparingTo("30.00");
    }

    @Test
    @DisplayName("mil chamadas pequenas somam mais que zero: as frações de centavo sobrevivem")
    void chamadasPequenasNaoDesaparecem() {
        // Uma chamada de 500 entrada / 100 saída custa 0,0025 + 0,0025 = 0,005 — meio centavo. Com
        // arredondamento para duas casas cada chamada viraria 0,01 ou 0,00, e o total do mês seria ficção.
        var one = OPUS.costOf(new TokenUsage(500, 100));
        assertThat(one).isEqualByComparingTo("0.005000");

        var thousand = IntStream.range(0, 1000)
                .mapToObj(i -> one)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(thousand).isEqualByComparingTo("5.00");
    }

    @Test
    @DisplayName("a estimativa antes da chamada usa o teto de saída, não uma expectativa")
    void estimativaUsaOPiorCaso() {
        // Verificar orçamento com o valor "provável" deixaria passar a chamada que estoura no pior caso —
        // e no pior caso o dinheiro já saiu.
        var ceiling = OPUS.ceilingCostOf(1_000, 4_000);

        assertThat(ceiling).isEqualByComparingTo(OPUS.costOf(new TokenUsage(1_000, 4_000)));
        assertThat(ceiling).isGreaterThan(OPUS.costOf(new TokenUsage(1_000, 100)));
    }

    @Test
    @DisplayName("chamada sem consumo custa zero, não erro")
    void semConsumoCustaZero() {
        assertThat(OPUS.costOf(TokenUsage.NONE)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("preço negativo não existe")
    void precoNegativoRecusado() {
        assertThatThrownBy(() -> new ModelPricing(new BigDecimal("-1"), BigDecimal.ONE, "USD"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("token negativo não existe")
    void tokenNegativoRecusado() {
        assertThatThrownBy(() -> new TokenUsage(-1, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
