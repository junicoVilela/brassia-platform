package br.com.brew.brassia.sensor.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * Grandeza medida por um dispositivo (INT-001): densidade, temperatura, pressão e vazão.
 *
 * <p><strong>Por que esta enum existe apesar de {@code fermentation.ReadingKind} existir.</strong> As duas
 * se parecem e não são a mesma coisa. {@code ReadingKind} descreve o que se mede <em>de um lote</em> e por
 * isso tem {@code PH} — que se mede com pHmetro na bancada, não com dispositivo na linha. Esta descreve o
 * que um <em>dispositivo</em> reporta e por isso tem {@code FLOW}, que é grandeza de tubulação e não de
 * fermentador. Compartilhar a enum criaria dependência entre dois módulos para economizar quatro
 * constantes, e amarraria a evolução de um à do outro: acrescentar uma grandeza de sensor mexeria no
 * domínio da fermentação.
 *
 * <p>A faixa de plausibilidade não rejeita: ela <strong>sinaliza</strong>. Ver
 * {@link ReadingQuality#OUT_OF_RANGE}.
 */
public enum Measure {

    DENSITY(List.of(
            new UnitRange("SG", "0.980", "1.180"),
            new UnitRange("PLATO", "-5", "40"))),
    TEMPERATURE(List.of(
            new UnitRange("C", "-10", "45"),
            new UnitRange("F", "14", "113"))),
    PRESSURE(List.of(
            new UnitRange("PSI", "0", "60"),
            new UnitRange("BAR", "0", "4"))),
    /**
     * Vazão. A faixa cobre da transferência mais lenta de uma bomba peristáltica até a linha de envase de
     * uma cervejaria pequena; acima disso é medidor com defeito ou unidade trocada, não produção.
     */
    FLOW(List.of(
            new UnitRange("L_MIN", "0", "500"),
            new UnitRange("HL_H", "0", "300")));

    private final List<UnitRange> units;

    Measure(List<UnitRange> units) {
        this.units = units;
    }

    public static Measure of(String raw) {
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

    /** {@code true} quando o valor cai fora da faixa plausível desta grandeza/unidade. */
    public boolean isImplausible(BigDecimal value, String unit) {
        var range = units.stream().filter(u -> u.unit().equals(unit)).findFirst().orElseThrow();
        return value.compareTo(range.min()) < 0 || value.compareTo(range.max()) > 0;
    }

    /** Descrição da faixa, para o motivo que acompanha a sinalização. */
    public String rangeOf(String unit) {
        var range = units.stream().filter(u -> u.unit().equals(unit)).findFirst().orElseThrow();
        return "[" + range.min().toPlainString() + ", " + range.max().toPlainString() + "]";
    }

    private record UnitRange(String unit, BigDecimal min, BigDecimal max) {
        UnitRange(String unit, String min, String max) {
            this(unit, new BigDecimal(min), new BigDecimal(max));
        }
    }
}
