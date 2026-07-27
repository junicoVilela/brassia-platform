package br.com.brew.brassia.production.adapter.outbound.persistence;

import br.com.brew.brassia.production.application.port.outbound.MeasurementRepository;
import br.com.brew.brassia.production.domain.Measurement;
import br.com.brew.brassia.production.domain.MeasurementKind;
import br.com.brew.brassia.production.domain.MeasurementSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcMeasurementRepository implements MeasurementRepository {

    private final JdbcClient jdbc;

    JdbcMeasurementRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Measurement m) {
        jdbc.sql("""
                INSERT INTO production_measurement (
                    id, brewery_id, batch_id, step_id, kind, measured_value, unit, temperature_c, method,
                    source, recorded_at, recorded_by)
                VALUES (:id, :brewery, :batch, :step, :kind, :value, :unit, :temp, :method,
                        :source, :at, :by)
                """)
                .param("id", m.id())
                .param("brewery", m.breweryId())
                .param("batch", m.batchId())
                .param("step", m.stepId())
                .param("kind", m.kind().name())
                .param("value", m.value())
                .param("unit", m.unit())
                .param("temp", m.temperatureC())
                .param("method", m.method())
                .param("source", m.source().name())
                .param("at", Timestamp.from(m.recordedAt()))
                .param("by", m.recordedBy())
                .update();
    }

    @Override
    public List<Measurement> findByBatch(UUID breweryId, UUID batchId) {
        return jdbc.sql("""
                SELECT id, brewery_id, batch_id, step_id, kind, measured_value, unit, temperature_c, method,
                       source, recorded_at, recorded_by
                FROM production_measurement
                WHERE brewery_id = :brewery AND batch_id = :batch
                ORDER BY recorded_at
                """)
                .param("brewery", breweryId).param("batch", batchId)
                .query((rs, n) -> map(rs))
                .list();
    }

    private Measurement map(ResultSet rs) throws SQLException {
        return Measurement.reconstitute(
                rs.getObject("id", UUID.class),
                rs.getObject("brewery_id", UUID.class),
                rs.getObject("batch_id", UUID.class),
                rs.getObject("step_id", UUID.class),
                MeasurementKind.valueOf(rs.getString("kind")),
                rs.getBigDecimal("measured_value"),
                rs.getString("unit"),
                rs.getBigDecimal("temperature_c"),
                rs.getString("method"),
                MeasurementSource.valueOf(rs.getString("source")),
                rs.getTimestamp("recorded_at").toInstant(),
                rs.getObject("recorded_by", UUID.class));
    }
}
