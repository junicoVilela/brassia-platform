package br.com.brew.brassia.water.domain;

import java.util.Locale;

/** Código do perfil-alvo de água — único por cervejaria, normalizado em maiúsculas. */
public record WaterProfileCode(String value) {
    private static final int MAX_LENGTH = 40;

    public WaterProfileCode {
        value = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (value.isBlank() || value.length() > MAX_LENGTH || !value.matches("[A-Z0-9-]+")) {
            throw new IllegalArgumentException("código deve ter 1 a 40 caracteres (A-Z, 0-9, -)");
        }
    }
}
