package br.com.brew.brassia.inventory.domain;

import java.util.Locale;

/** Resultado da inspeção no recebimento. Só {@code APPROVED} fica disponível. */
public enum StockInspection {
    APPROVED, BLOCKED;

    public static StockInspection of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("inspeção é obrigatória");
        }
        try {
            return StockInspection.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("inspeção inválida: " + value);
        }
    }
}
