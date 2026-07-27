package br.com.brew.brassia.sanitation.domain;

import java.util.Locale;

/** Nível de risco de contaminação (CLN-002). */
public enum RiskLevel {
    BAIXO,
    MEDIO,
    ALTO;

    public static RiskLevel of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("risco obrigatório");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("risco inválido: " + raw);
        }
    }
}
