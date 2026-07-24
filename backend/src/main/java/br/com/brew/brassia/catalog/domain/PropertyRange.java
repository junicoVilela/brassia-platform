package br.com.brew.brassia.catalog.domain;

import java.math.BigDecimal;

/**
 * Faixa de referência de uma propriedade técnica (ex.: alfa-ácido, atenuação,
 * cor). O catálogo guarda faixas; valores por safra/lote pertencem ao estoque.
 * Aceita ausência (limites nulos) e registra a unidade original.
 */
public record PropertyRange(BigDecimal min, BigDecimal max, String unit) {

    public PropertyRange {
        unit = unit == null || unit.isBlank() ? null : unit.trim();
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new IllegalArgumentException("min não pode ser maior que max");
        }
    }

    public static PropertyRange none() {
        return new PropertyRange(null, null, null);
    }

    public boolean isEmpty() {
        return min == null && max == null;
    }
}
