package br.com.brew.brassia.production.adapter.outbound.persistence;

import br.com.brew.brassia.production.BatchMeasurementLookup;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * A série de medições de um lote (SPC-001).
 *
 * <p>Ordenada por {@code recorded_at}, e a ordem é o contrato: sequência e tendência só existem no tempo.
 * Ordenar por id ou por valor produziria sinais que o processo nunca deu.
 */
@Repository
class JdbcBatchMeasurementLookup implements BatchMeasurementLookup {

    private final JdbcClient jdbc;

    JdbcBatchMeasurementLookup(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Reading> ofBatch(UUID breweryId, UUID batchId, String kind) {
        return jdbc.sql("""
                SELECT measured_value, unit, recorded_at
                FROM production_measurement
                WHERE brewery_id = :brewery AND batch_id = :batch AND kind = :kind
                ORDER BY recorded_at
                """)
                .param("brewery", breweryId).param("batch", batchId).param("kind", kind)
                .query((rs, n) -> new Reading(
                        rs.getBigDecimal("measured_value"),
                        rs.getString("unit"),
                        rs.getTimestamp("recorded_at").toInstant()))
                .list();
    }
}
