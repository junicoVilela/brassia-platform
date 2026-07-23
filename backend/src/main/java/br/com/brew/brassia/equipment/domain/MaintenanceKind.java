package br.com.brew.brassia.equipment.domain;

import java.util.Locale;

/** Natureza da janela de indisponibilidade do equipamento. */
public enum MaintenanceKind {
    MAINTENANCE,
    CALIBRATION;

    public static MaintenanceKind of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("tipo de manutenção obrigatório");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("tipo de manutenção inválido");
        }
    }
}
