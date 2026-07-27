package br.com.brew.brassia.production.domain;

import java.util.Locale;
import java.util.Set;

/**
 * Grandeza medida no dia de brassa (PRD-003), com vocabulário fechado de unidades
 * válidas. Unidade fora da grandeza é rejeitada (medição com unidade incompatível).
 */
public enum MeasurementKind {
    DENSITY(Set.of("SG", "PLATO")),
    TEMPERATURE(Set.of("C", "F")),
    VOLUME(Set.of("L", "ML")),
    PH(Set.of("PH")),
    COLOR(Set.of("EBC", "SRM")),
    IBU(Set.of("IBU"));

    private final Set<String> units;

    MeasurementKind(Set<String> units) {
        this.units = units;
    }

    public static MeasurementKind of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("grandeza obrigatória");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("grandeza inválida: " + raw);
        }
    }

    /** Normaliza e valida a unidade para esta grandeza; lança se incompatível. */
    public String requireUnit(String rawUnit) {
        if (rawUnit == null || rawUnit.isBlank()) {
            throw new IllegalArgumentException("unidade obrigatória");
        }
        var unit = rawUnit.trim().toUpperCase(Locale.ROOT);
        if (!units.contains(unit)) {
            throw new IllegalArgumentException(
                    "unidade " + unit + " incompatível com a grandeza " + name() + " (válidas: " + units + ")");
        }
        return unit;
    }
}
