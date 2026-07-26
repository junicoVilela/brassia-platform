package br.com.brew.brassia.inventory.domain;

import java.util.Locale;

/** Unidade do lote de estoque. */
public enum StockUnit {
    KG, G, MG, L, ML, UNIT;

    public static StockUnit of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("unidade é obrigatória");
        }
        try {
            return StockUnit.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unidade inválida: " + value);
        }
    }
}
