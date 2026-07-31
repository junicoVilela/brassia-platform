package br.com.brew.brassia.fermentation.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * Grandeza de uma leitura de fermentação (FER-002): densidade, temperatura, pressão e pH.
 * Cada unidade válida traz uma faixa de plausibilidade; um valor fora dela não é rejeitado,
 * mas sinalizado como inválido (sensor ruidoso).
 */
public enum ReadingKind {
    DENSITY(List.of(
            new UnitRange("SG", "0.980", "1.180"),
            new UnitRange("PLATO", "-5", "40"))),
    TEMPERATURE(List.of(
            new UnitRange("C", "-10", "45"),
            new UnitRange("F", "14", "113"))),
    PRESSURE(List.of(
            new UnitRange("PSI", "0", "60"),
            new UnitRange("BAR", "0", "4"))),
    PH(List.of(
            new UnitRange("PH", "2.5", "7.5")));

    private final List<UnitRange> units;

    ReadingKind(List<UnitRange> units) {
        this.units = units;
    }

    public static ReadingKind of(String raw) {
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
        return units.stream().filter(u -> u.unit().equals(unit)).findFirst()
                .map(UnitRange::unit)
                .orElseThrow(() -> new IllegalArgumentException(
                        "unidade " + unit + " incompatível com a grandeza " + name()));
    }

    /** {@code null} se plausível; senão o motivo da sinalização (fora da faixa). */
    public String implausibleReason(BigDecimal value, String unit) {
        var range = units.stream().filter(u -> u.unit().equals(unit)).findFirst().orElseThrow();
        if (value.compareTo(range.min()) < 0 || value.compareTo(range.max()) > 0) {
            return name() + " " + value.toPlainString() + " " + unit + " fora da faixa plausível ["
                    + range.min().toPlainString() + ", " + range.max().toPlainString() + "]";
        }
        return null;
    }

    private record UnitRange(String unit, BigDecimal min, BigDecimal max) {
        UnitRange(String unit, String min, String max) {
            this(unit, new BigDecimal(min), new BigDecimal(max));
        }
    }
}
