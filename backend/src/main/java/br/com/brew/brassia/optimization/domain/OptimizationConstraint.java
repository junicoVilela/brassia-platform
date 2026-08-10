package br.com.brew.brassia.optimization.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Uma restrição explícita (OPT-001).
 *
 * <p><strong>Restrição não é preferência, e a diferença é dura.</strong> Uma solução que viola uma
 * restrição não é uma solução pior — ela não é solução. Por isso restrições não entram no score: um peso
 * alto o bastante sempre acaba comprando a violação, e o resultado sai apresentado como ótimo tendo
 * quebrado o que não podia.
 */
public record OptimizationConstraint(
        ConstraintKind kind,
        BigDecimal minValue,
        BigDecimal maxValue,
        UUID ingredientId) {

    public OptimizationConstraint {
        Objects.requireNonNull(kind, "kind");
        switch (kind) {
            case KEEP_INGREDIENT, EXCLUDE_INGREDIENT -> Objects.requireNonNull(ingredientId,
                    "restrição de ingrediente precisa dizer qual");
            case MAX_COST_PER_LITER -> Objects.requireNonNull(maxValue, "informe o teto de custo");
            case IBU_RANGE, COLOR_RANGE -> {
                Objects.requireNonNull(minValue, "informe o mínimo da faixa");
                Objects.requireNonNull(maxValue, "informe o máximo da faixa");
                if (minValue.compareTo(maxValue) > 0) {
                    throw new IllegalArgumentException("faixa invertida: " + minValue + " > " + maxValue);
                }
            }
            case STOCK_ONLY -> { /* não precisa de parâmetro */ }
        }
    }

    public static OptimizationConstraint range(ConstraintKind kind, BigDecimal min, BigDecimal max) {
        return new OptimizationConstraint(kind, min, max, null);
    }

    public static OptimizationConstraint ceiling(ConstraintKind kind, BigDecimal max) {
        return new OptimizationConstraint(kind, null, max, null);
    }

    public static OptimizationConstraint about(ConstraintKind kind, UUID ingredientId) {
        return new OptimizationConstraint(kind, null, null, ingredientId);
    }

    public static OptimizationConstraint flag(ConstraintKind kind) {
        return new OptimizationConstraint(kind, null, null, null);
    }

    /** Se um valor cabe na faixa. Fora dela a candidata é descartada, não penalizada. */
    public boolean admits(BigDecimal value) {
        if (value == null) {
            return false;
        }
        if (minValue != null && value.compareTo(minValue) < 0) {
            return false;
        }
        return maxValue == null || value.compareTo(maxValue) <= 0;
    }

    public Optional<UUID> ingredient() {
        return Optional.ofNullable(ingredientId);
    }
}
