package br.com.brew.brassia.utilities.domain;

import br.com.brew.brassia.utilities.UtilityReadingSource.Coverage;
import br.com.brew.brassia.utilities.UtilityReadingSource.Reading;
import br.com.brew.brassia.utilities.UtilityReadingSource.UtilityType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * Consumo de uma utilidade por litro envasado, num período (UTL-001).
 *
 * <p><strong>Medido e estimado não se somam num número só.</strong> É o critério da história, e o
 * motivo é prático: um indicador que mistura leitura de hidrômetro com conta de padeiro não serve
 * para provar nada a auditor nenhum, e serve menos ainda para saber se a fábrica melhorou. Os dois
 * totais viajam separados, e o total geral existe para quem quiser somá-los sabendo o que fez.
 *
 * <p><strong>Sem litro envasado, não há indicador — e não é zero.</strong> Um período em que a
 * fábrica limpou tanque e não envasou nada gastou água sem produzir cerveja; dizer "0 L/L" seria
 * dizer que ela foi eficiente. O indicador vem vazio, e o consumo aparece do mesmo jeito.
 */
public record UtilityIndicator(UtilityType type, String unit, BigDecimal measured, BigDecimal estimated,
        BigDecimal packagedLiters, List<Coverage> coverage, List<String> sources) {

    public UtilityIndicator {
        Objects.requireNonNull(type, "tipo é obrigatório");
        Objects.requireNonNull(unit, "unidade é obrigatória");
        measured = measured == null ? BigDecimal.ZERO : measured;
        estimated = estimated == null ? BigDecimal.ZERO : estimated;
        packagedLiters = packagedLiters == null ? BigDecimal.ZERO : packagedLiters;
        coverage = List.copyOf(coverage);
        sources = List.copyOf(sources);
    }

    public static UtilityIndicator of(UtilityType type, List<Reading> readings, BigDecimal packagedLiters,
            List<Coverage> coverage) {
        var measured = readings.stream()
                .filter(Reading::measured)
                .map(Reading::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var estimated = readings.stream()
                .filter(reading -> !reading.measured())
                .map(Reading::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var sources = readings.stream().map(Reading::source).distinct().sorted().toList();
        return new UtilityIndicator(type, unitOf(type), measured, estimated, packagedLiters, coverage,
                sources);
    }

    public BigDecimal total() {
        return measured.add(estimated);
    }

    /** Consumo por litro envasado; vazio quando nada foi envasado no período. */
    public BigDecimal perLiter() {
        if (packagedLiters.signum() <= 0) {
            return null;
        }
        return total().divide(packagedLiters, 4, RoundingMode.HALF_UP);
    }

    /** Só a parte medida, por litro — é a que se leva a auditoria. */
    public BigDecimal measuredPerLiter() {
        if (packagedLiters.signum() <= 0) {
            return null;
        }
        return measured.divide(packagedLiters, 4, RoundingMode.HALF_UP);
    }

    /**
     * Verdadeiro quando todo evento que deveria ter medição no período teve.
     *
     * <p>Sem cobertura declarada por ninguém, a resposta é falsa e não verdadeira: não saber quanto
     * foi medido não é o mesmo que ter medido tudo.
     */
    public boolean fullyMeasured() {
        return !coverage.isEmpty() && coverage.stream().allMatch(Coverage::complete);
    }

    private static String unitOf(UtilityType type) {
        return switch (type) {
            case WATER -> "L";
            case ENERGY -> "kWh";
            case CO2, CLEANING_PRODUCT -> "kg";
        };
    }
}
