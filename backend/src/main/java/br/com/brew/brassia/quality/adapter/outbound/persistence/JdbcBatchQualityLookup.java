package br.com.brew.brassia.quality.adapter.outbound.persistence;

import br.com.brew.brassia.quality.BatchQualityLookup;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Medições, desvios e não conformidades de um lote (RPT-001).
 *
 * <p>A não conformidade não aponta para o lote: ela aponta para o desvio, que aponta para a
 * medição, que aponta para o lote. O caminho é longo de propósito — NC também nasce de reclamação
 * e de auditoria, sem lote nenhum —, e é por ele que se chega às NCs deste lote.
 */
@Component
class JdbcBatchQualityLookup implements BatchQualityLookup {

    private final JdbcClient jdbc;

    JdbcBatchQualityLookup(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public BatchQuality ofBatch(UUID breweryId, UUID batchId) {
        var totals = jdbc.sql("""
                SELECT COUNT(*) AS total, COUNT(*) FILTER (WHERE within_spec) AS within
                FROM quality_measurement
                WHERE brewery_id = :brewery AND batch_id = :batch
                """)
                .param("brewery", breweryId).param("batch", batchId)
                .query((rs, rowNum) -> new int[] {rs.getInt("total"), rs.getInt("within")})
                .single();

        return new BatchQuality(totals[0], totals[1], outOfSpec(breweryId, batchId),
                deviations(breweryId, batchId), nonConformities(breweryId, batchId));
    }

    private List<Measurement> outOfSpec(UUID breweryId, UUID batchId) {
        return jdbc.sql("""
                SELECT parameter, value, unit, measured_at
                FROM quality_measurement
                WHERE brewery_id = :brewery AND batch_id = :batch AND NOT within_spec
                ORDER BY measured_at
                """)
                .param("brewery", breweryId).param("batch", batchId)
                .query((rs, rowNum) -> new Measurement(rs.getString("parameter"),
                        rs.getBigDecimal("value"), rs.getString("unit"),
                        rs.getTimestamp("measured_at").toInstant()))
                .list();
    }

    private List<Deviation> deviations(UUID breweryId, UUID batchId) {
        return jdbc.sql("""
                SELECT d.parameter, d.severity, d.status, d.limit_value, d.measured_value, d.unit,
                       d.opened_at
                FROM quality_deviation d
                JOIN quality_measurement m ON m.id = d.measurement_id AND m.brewery_id = d.brewery_id
                WHERE d.brewery_id = :brewery AND m.batch_id = :batch
                ORDER BY d.opened_at
                """)
                .param("brewery", breweryId).param("batch", batchId)
                .query((rs, rowNum) -> new Deviation(rs.getString("parameter"), rs.getString("severity"),
                        rs.getString("status"), rs.getBigDecimal("limit_value"),
                        rs.getBigDecimal("measured_value"), rs.getString("unit"),
                        rs.getTimestamp("opened_at").toInstant()))
                .list();
    }

    private List<NonConformity> nonConformities(UUID breweryId, UUID batchId) {
        return jdbc.sql("""
                SELECT DISTINCT n.code, n.title, n.severity, n.status
                FROM quality_non_conformity n
                JOIN quality_deviation d ON d.id = n.deviation_id AND d.brewery_id = n.brewery_id
                JOIN quality_measurement m ON m.id = d.measurement_id AND m.brewery_id = d.brewery_id
                WHERE n.brewery_id = :brewery AND m.batch_id = :batch
                ORDER BY n.code
                """)
                .param("brewery", breweryId).param("batch", batchId)
                .query((rs, rowNum) -> new NonConformity(rs.getString("code"), rs.getString("title"),
                        rs.getString("severity"), rs.getString("status")))
                .list();
    }
}
