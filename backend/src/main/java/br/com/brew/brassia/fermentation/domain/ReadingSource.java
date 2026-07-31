package br.com.brew.brassia.fermentation.domain;

import java.util.Locale;

/** Origem de uma leitura de fermentação (FER-002): manual ou de sensor. */
public enum ReadingSource {
    MANUAL,
    SENSOR;

    public static ReadingSource of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("origem obrigatória");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("origem inválida: " + raw);
        }
    }
}
