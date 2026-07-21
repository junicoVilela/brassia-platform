package br.com.brew.brassia.brewery.domain;

import java.util.Locale;

/**
 * Código da cervejaria. Normalizado em maiúsculas (autoridade de unicidade) e
 * restrito a letras/dígitos/hífen para servir como identificador estável.
 */
public record BreweryCode(String value) {
    private static final int MAX_LENGTH = 40;

    public BreweryCode {
        value = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (value.isBlank() || value.length() > MAX_LENGTH || !value.matches("[A-Z0-9-]+")) {
            throw new IllegalArgumentException("código deve ter 1 a 40 caracteres (A-Z, 0-9, -)");
        }
    }
}
