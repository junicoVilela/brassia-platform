package br.com.brew.brassia.ai.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Preço de um modelo, por milhão de tokens e por direção (AIA-001).
 *
 * <p>O preço é configuração, não constante de código: ele muda por contrato e por provedor, e um
 * número cravado numa classe viraria uma conta errada silenciosa no dia em que mudar.
 *
 * <p><strong>Seis casas na conta, não duas.</strong> Uma chamada custa frações de centavo; arredondar
 * cada chamada para centavos zeraria quase todas e o total do mês daria zero. O arredondamento para
 * moeda é da borda de apresentação, não daqui.
 */
public record ModelPricing(BigDecimal inputPerMillion, BigDecimal outputPerMillion, String currency) {

    private static final BigDecimal MILLION = new BigDecimal("1000000");

    /** Casas decimais do custo acumulado: menos que isto perde chamadas inteiras no arredondamento. */
    public static final int COST_SCALE = 6;

    public ModelPricing {
        Objects.requireNonNull(inputPerMillion, "preço de entrada é obrigatório");
        Objects.requireNonNull(outputPerMillion, "preço de saída é obrigatório");
        currency = Objects.requireNonNull(currency, "moeda é obrigatória");
        if (inputPerMillion.signum() < 0 || outputPerMillion.signum() < 0) {
            throw new IllegalArgumentException("preço não pode ser negativo");
        }
    }

    public BigDecimal costOf(TokenUsage usage) {
        Objects.requireNonNull(usage, "usage");
        var input = inputPerMillion.multiply(BigDecimal.valueOf(usage.inputTokens()));
        var output = outputPerMillion.multiply(BigDecimal.valueOf(usage.outputTokens()));
        return input.add(output).divide(MILLION, COST_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Custo do pior caso desta chamada: o teto de saída consumido inteiro.
     *
     * <p>O orçamento é verificado antes de gastar, e antes de gastar ninguém sabe quantos tokens a
     * resposta terá. Estimar pelo teto erra para o lado de recusar uma chamada que caberia — que é o
     * lado certo de errar quando o assunto é dinheiro que já saiu.
     */
    public BigDecimal ceilingCostOf(long inputTokens, int maxOutputTokens) {
        return costOf(new TokenUsage(inputTokens, maxOutputTokens));
    }
}
