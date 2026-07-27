package br.com.brew.brassia.inventory.domain;

import java.util.Locale;

/** Origem do valor medido vinculado ao lote (STK-005): manual, importado ou sugerido. */
public enum LotPropertySource {
    MANUAL,
    IMPORTED,
    SUGGESTED;

    public static LotPropertySource of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("fonte obrigatória");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("fonte inválida");
        }
    }
}
