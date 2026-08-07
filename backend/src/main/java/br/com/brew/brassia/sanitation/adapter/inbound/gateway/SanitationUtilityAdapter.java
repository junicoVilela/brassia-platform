package br.com.brew.brassia.sanitation.adapter.inbound.gateway;

import br.com.brew.brassia.utilities.UtilityReadingSource;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * A água, a energia e o produto que a limpeza mede (CLN-005), publicados para o indicador (UTL-001).
 *
 * <p>O instante que conta é o do <strong>registro do consumo</strong>, não o do início do ciclo: é
 * quando alguém leu o hidrômetro. Um ciclo que começou em julho e teve o consumo lançado em agosto
 * pertence ao mês em que foi medido, e o contrário faria o número de um mês fechado mudar depois.
 *
 * <p>A cobertura é a parte que o indicador não consegue adivinhar: quantos ciclos do período
 * <em>deveriam</em> ter medição. Só a sanitização sabe quantos ciclos encerrou.
 */
@Component
class SanitationUtilityAdapter implements UtilityReadingSource {

    private final JdbcClient jdbc;

    SanitationUtilityAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Reading> readingsIn(UUID breweryId, Instant from, Instant to) {
        var readings = new ArrayList<Reading>();
        jdbc.sql("""
                SELECT procedure_code, water_liters, energy_kwh, product_kg, consumption_at
                FROM sanitation_cleaning_cycle
                WHERE brewery_id = :brewery AND consumption_at IS NOT NULL
                  AND consumption_at >= :from AND consumption_at < :to
                ORDER BY consumption_at
                """)
                .param("brewery", breweryId)
                .param("from", Timestamp.from(from)).param("to", Timestamp.from(to))
                .query((rs, rowNum) -> {
                    var at = rs.getTimestamp("consumption_at").toInstant();
                    var source = "ciclo de limpeza " + rs.getString("procedure_code");
                    var found = new ArrayList<Reading>();
                    add(found, UtilityType.WATER, rs.getBigDecimal("water_liters"), at, source);
                    add(found, UtilityType.ENERGY, rs.getBigDecimal("energy_kwh"), at, source);
                    add(found, UtilityType.CLEANING_PRODUCT, rs.getBigDecimal("product_kg"), at, source);
                    return found;
                })
                .list()
                .forEach(readings::addAll);
        return readings;
    }

    @Override
    public List<Coverage> coverageIn(UUID breweryId, Instant from, Instant to) {
        // Ciclos encerrados no período versus ciclos com consumo lançado: é o que diz se o
        // indicador fala pela fábrica inteira ou por um pedaço dela.
        return jdbc.sql("""
                SELECT COUNT(*) FILTER (WHERE water_liters IS NOT NULL OR energy_kwh IS NOT NULL)
                           AS reported,
                       COUNT(*) AS expected
                FROM sanitation_cleaning_cycle
                WHERE brewery_id = :brewery AND ended_at IS NOT NULL
                  AND ended_at >= :from AND ended_at < :to
                """)
                .param("brewery", breweryId)
                .param("from", Timestamp.from(from)).param("to", Timestamp.from(to))
                .query((rs, rowNum) -> new Coverage("ciclos de limpeza encerrados",
                        rs.getInt("reported"), rs.getInt("expected")))
                .list().stream()
                .filter(coverage -> coverage.expected() > 0)
                .toList();
    }

    private static void add(List<Reading> readings, UtilityType type, java.math.BigDecimal amount,
            Instant at, String source) {
        if (amount != null && amount.signum() > 0) {
            // Medição de gente lendo instrumento: é leitura, não estimativa.
            readings.add(new Reading(type, amount, at, source, true));
        }
    }
}
