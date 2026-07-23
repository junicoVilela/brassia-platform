package br.com.brew.brassia.equipment.domain;

import java.util.Locale;

/** Código do equipamento — único por cervejaria, normalizado em maiúsculas. */
public record EquipmentCode(String value) {
    private static final int MAX_LENGTH = 40;

    public EquipmentCode {
        value = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (value.isBlank() || value.length() > MAX_LENGTH || !value.matches("[A-Z0-9-]+")) {
            throw new IllegalArgumentException("código deve ter 1 a 40 caracteres (A-Z, 0-9, -)");
        }
    }
}
