package br.com.brew.brassia.water.domain;

import java.util.Locale;

/** Método de obtenção do laudo de água. */
public enum WaterMethod {
    LAB,
    TEST_STRIP,
    ION_METER,
    UTILITY;

    public static WaterMethod of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("método obrigatório");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("método inválido");
        }
    }
}
