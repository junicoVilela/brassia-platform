package br.com.brew.brassia.catalog.domain;

import java.util.Locale;

/** Vocabulário fechado de unidades de uso e de compra (CAT-001). */
public enum MeasurementUnit {
    KG,
    G,
    MG,
    L,
    ML,
    UNIT,
    PACK;

    public static MeasurementUnit of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("unidade obrigatória");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unidade inválida");
        }
    }
}
