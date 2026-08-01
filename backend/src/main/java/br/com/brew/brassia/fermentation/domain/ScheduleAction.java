package br.com.brew.brassia.fermentation.domain;

import java.util.Arrays;
import java.util.Locale;

/** Ação de uma etapa da agenda de fermentação (FER-004). */
public enum ScheduleAction {
    RAMP,
    REST,
    DRY_HOP,
    COLD_CRASH,
    TRANSFER,
    CONDITIONING;

    public static ScheduleAction of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ação da etapa é obrigatória");
        }
        var normalized = value.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(a -> a.name().equals(normalized)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("ação inválida: " + value));
    }
}
