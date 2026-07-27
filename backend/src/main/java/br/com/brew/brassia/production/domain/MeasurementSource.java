package br.com.brew.brassia.production.domain;

import java.util.Locale;

/** Origem da medição (PRD-003): manual, de instrumento/dispositivo ou importada. */
public enum MeasurementSource {
    MANUAL,
    DEVICE,
    IMPORTED;

    public static MeasurementSource of(String raw) {
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
