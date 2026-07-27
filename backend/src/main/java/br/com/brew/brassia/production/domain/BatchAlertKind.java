package br.com.brew.brassia.production.domain;

import java.util.Locale;

/** Tipo de item da linha do tempo operacional do lote (PRD-006). */
public enum BatchAlertKind {
    ADDITION,
    STEP,
    MEASUREMENT,
    DECISION;

    public static BatchAlertKind of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("tipo de alerta obrigatório");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("tipo de alerta inválido: " + raw);
        }
    }
}
