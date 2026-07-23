package br.com.brew.brassia.recipe.domain;

import java.math.BigDecimal;

/**
 * Metas cervejeiras informadas na receita (inputs). Todas opcionais; o cálculo
 * efetivo é de sprints seguintes (REC-002/003). Quando presentes, não-negativas.
 */
public record RecipeTargets(BigDecimal ogPoints, BigDecimal ibu, BigDecimal colorEbc, BigDecimal abv) {
    public RecipeTargets {
        ogPoints = nonNegative(ogPoints, "OG");
        ibu = nonNegative(ibu, "IBU");
        colorEbc = nonNegative(colorEbc, "cor");
        abv = nonNegative(abv, "ABV");
    }

    public static RecipeTargets none() {
        return new RecipeTargets(null, null, null, null);
    }

    private static BigDecimal nonNegative(BigDecimal value, String field) {
        if (value != null && value.signum() < 0) {
            throw new IllegalArgumentException(field + " não pode ser negativo");
        }
        return value;
    }
}
