package br.com.brew.brassia.sanitation.domain;

import java.util.Locale;

/** Material da superfície (CLN-002). Sem herança entre materiais (madeira/plástico ≠ inox). */
public enum EquipmentMaterial {
    INOX,
    ALUMINIO,
    PLASTICO,
    MADEIRA,
    VIDRO,
    BORRACHA;

    public static EquipmentMaterial of(String raw) {
        return parse(raw, "material");
    }

    static EquipmentMaterial parse(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(field + " obrigatório");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(field + " inválido: " + raw);
        }
    }
}
