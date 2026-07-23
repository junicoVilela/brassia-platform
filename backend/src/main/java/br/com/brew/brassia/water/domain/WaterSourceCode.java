package br.com.brew.brassia.water.domain;

import java.util.Locale;

/** Código da fonte de água — único por cervejaria, normalizado em maiúsculas. */
public record WaterSourceCode(String value) {
    private static final int MAX_LENGTH = 40;

    public WaterSourceCode {
        value = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (value.isBlank() || value.length() > MAX_LENGTH || !value.matches("[A-Z0-9-]+")) {
            throw new IllegalArgumentException("código deve ter 1 a 40 caracteres (A-Z, 0-9, -)");
        }
    }
}
