package br.com.brew.brassia.recipe.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Item da composição da receita: um ingrediente (referência ao catálogo) com
 * quantidade, unidade e etapa. Percentual é opcional (usado no balanço de grãos).
 */
public record RecipeItem(
        UUID ingredientId,
        RecipeStage stage,
        BigDecimal quantity,
        RecipeUnit unit,
        Integer timingMinutes,
        BigDecimal percentage) {

    public RecipeItem {
        Objects.requireNonNull(ingredientId, "ingredientId");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(unit, "unit");
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantidade deve ser positiva");
        }
        if (timingMinutes != null && timingMinutes < 0) {
            throw new IllegalArgumentException("tempo não pode ser negativo");
        }
        if (percentage != null && (percentage.signum() < 0 || percentage.compareTo(new BigDecimal("100")) > 0)) {
            throw new IllegalArgumentException("percentual deve estar entre 0 e 100");
        }
    }
}
