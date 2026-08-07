package br.com.brew.brassia.gas.adapter.inbound.gateway;

import br.com.brew.brassia.utilities.UtilityReadingSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * O CO₂ consumido na rede (GAS-001), publicado para o indicador (UTL-001).
 *
 * <p>Só o consumo lançado entra, e ele é lançado à mão a partir da pesagem do cilindro — é medição,
 * não rateio. O gás que vazou sem ninguém pesar não aparece aqui, e é isso que a cobertura tem de
 * dizer; por ora ela não é declarada, porque não existe "consumo esperado" de CO₂ contra o qual
 * comparar. É a lacuna UTL-001-A.
 */
@Component
class GasUtilityAdapter implements UtilityReadingSource {

    private final JdbcClient jdbc;

    GasUtilityAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Reading> readingsIn(UUID breweryId, Instant from, Instant to) {
        return jdbc.sql("""
                SELECT c.kg, c.recorded_at, y.code AS cylinder_code
                FROM gas_consumption c
                JOIN gas_cylinder y ON y.id = c.cylinder_id AND y.brewery_id = c.brewery_id
                WHERE c.brewery_id = :brewery AND c.recorded_at >= :from AND c.recorded_at < :to
                ORDER BY c.recorded_at
                """)
                .param("brewery", breweryId)
                .param("from", Timestamp.from(from)).param("to", Timestamp.from(to))
                .query((rs, rowNum) -> new Reading(UtilityType.CO2, rs.getBigDecimal("kg"),
                        rs.getTimestamp("recorded_at").toInstant(),
                        "cilindro " + rs.getString("cylinder_code"), true))
                .list();
    }
}
