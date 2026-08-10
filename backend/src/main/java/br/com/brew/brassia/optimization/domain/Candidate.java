package br.com.brew.brassia.optimization.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Uma alternativa avaliada (OPT-001).
 *
 * <p><strong>O que ela custa vem junto com o que ela ganha.</strong> Uma alternativa que aparece só com o
 * ganho — "8% mais barata" — esconde que ela mudou a cor em 4 EBC, e quem escolhe decide sem saber o que
 * está trocando. Por isso {@link #tradeOffs()} é campo obrigatório e não anexo opcional.
 */
public record Candidate(
        String label,
        List<Substitution> substitutions,
        BigDecimal costPerLiter,
        BigDecimal estimatedIbu,
        BigDecimal estimatedColorEbc,
        BigDecimal score,
        List<TradeOff> tradeOffs) {

    public Candidate {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(score, "score");
        substitutions = List.copyOf(Objects.requireNonNull(substitutions, "substitutions"));
        tradeOffs = List.copyOf(Objects.requireNonNull(tradeOffs, "tradeOffs"));
    }

    /** A troca de um ingrediente por outro, com as quantidades. */
    public record Substitution(java.util.UUID fromIngredientId, String fromLabel,
            java.util.UUID toIngredientId, String toLabel, BigDecimal quantity, String unit) {
    }

    /**
     * O que piorou, em relação à receita original.
     *
     * <p>Só o que piorou: listar as melhorias aqui diluiria a leitura e faria o custo parecer menor do
     * que é. O que melhorou já está no objetivo.
     */
    public record TradeOff(String dimension, String description, BigDecimal originalValue,
            BigDecimal candidateValue) {
    }
}
