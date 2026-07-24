package br.com.brew.brassia.recipe.domain;

import java.math.BigDecimal;

/** Faixa alvo de um atributo (estilo oficial ou perfil personalizado). */
public record AttributeRange(BigDecimal min, BigDecimal max, String unit) {

    public AttributeRange {
        unit = unit == null || unit.isBlank() ? null : unit.trim();
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new IllegalArgumentException("min não pode ser maior que max");
        }
    }

    public boolean isEmpty() {
        return min == null && max == null;
    }
}
