package br.com.brew.brassia.gas.domain;

import java.util.Locale;

/** Gás do cilindro (GAS-001). Mistura é tratada como um tipo próprio; a composição é do rótulo. */
public enum GasType {
    CO2,
    N2,
    MIX;

    public static GasType of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("tipo de gás é obrigatório");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("tipo de gás inválido");
        }
    }
}
