package br.com.brew.brassia.sanitation.domain;

import java.util.Locale;

/** Nível de sujidade (CLN-002). */
public enum SoilingLevel {
    LEVE,
    MODERADA,
    PESADA;

    public static SoilingLevel of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("sujidade obrigatória");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("sujidade inválida: " + raw);
        }
    }
}
