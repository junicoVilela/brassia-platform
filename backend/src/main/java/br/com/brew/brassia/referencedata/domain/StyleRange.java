package br.com.brew.brassia.referencedata.domain;

import java.math.BigDecimal;

/**
 * Faixa de um parâmetro de estilo (OG, FG, ABV, IBU, cor). Aceita ausência
 * (limites nulos = faixa aberta ou não informada) e registra a unidade original.
 */
public record StyleRange(BigDecimal min, BigDecimal max, String unit) {

    public StyleRange {
        unit = unit == null || unit.isBlank() ? null : unit.trim();
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new IllegalArgumentException("min não pode ser maior que max");
        }
    }

    public static StyleRange none() {
        return new StyleRange(null, null, null);
    }

    public boolean isEmpty() {
        return min == null && max == null;
    }

    /** Valor dentro da faixa (limites nulos são abertos). Null nunca conforma. */
    public boolean contains(BigDecimal value) {
        if (value == null) {
            return false;
        }
        if (min != null && value.compareTo(min) < 0) {
            return false;
        }
        return max == null || value.compareTo(max) <= 0;
    }
}
