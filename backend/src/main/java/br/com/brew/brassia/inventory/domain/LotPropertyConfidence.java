package br.com.brew.brassia.inventory.domain;

import java.util.Locale;

/** Confiança qualitativa do valor vinculado ao lote (STK-005). */
public enum LotPropertyConfidence {
    HIGH,
    MEDIUM,
    LOW;

    public static LotPropertyConfidence of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("confiança obrigatória");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("confiança inválida");
        }
    }
}
