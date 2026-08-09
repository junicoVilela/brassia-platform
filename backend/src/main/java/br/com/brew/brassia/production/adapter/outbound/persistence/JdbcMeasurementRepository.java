package br.com.brew.brassia.production.adapter.outbound.persistence;

import br.com.brew.brassia.production.application.port.outbound.MeasurementRepository;
import br.com.brew.brassia.production.domain.Measurement;
import br.com.brew.brassia.production.domain.MeasurementKind;
import br.com.brew.brassia.production.domain.MeasurementSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
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
                    source, recorded_at, recorded_by, client_request_id)
                VALUES (:id, :brewery, :batch, :step, :kind, :value, :unit, :temp, :method,
                        :source, :at, :by, :clientRequest)
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
                .param("clientRequest", m.clientRequestId())
                .update();
    }

    /**
     * Insere ignorando repetição do mesmo apontamento (PWA-002).
     *
     * <p>{@code ON CONFLICT} sobre o índice único parcial: quem decide é o banco, não uma consulta
     * anterior. O conflito é <strong>silencioso</strong> porque repetição aqui não é erro — a fila do
     * aparelho reenvia até receber confirmação, e é assim que ela não perde o apontamento de quem estava
     * sem rede.
     */
    @Override
    public boolean insertIfAbsent(Measurement m) {
        return jdbc.sql("""
                INSERT INTO production_measurement (
                    id, brewery_id, batch_id, step_id, kind, measured_value, unit, temperature_c, method,
                    source, recorded_at, recorded_by, client_request_id)
                VALUES (:id, :brewery, :batch, :step, :kind, :value, :unit, :temp, :method,
                        :source, :at, :by, :clientRequest)
                ON CONFLICT (brewery_id, client_request_id) WHERE client_request_id IS NOT NULL
                DO NOTHING
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
                .param("clientRequest", m.clientRequestId())
                .update() == 1;
    }

    @Override
    public Optional<Measurement> byClientRequestId(UUID breweryId, String clientRequestId) {
        return jdbc.sql("""
                SELECT id, brewery_id, batch_id, step_id, kind, measured_value, unit, temperature_c, method,
                       source, recorded_at, recorded_by, client_request_id
                FROM production_measurement
                WHERE brewery_id = :brewery AND client_request_id = :clientRequest
                """)
                .param("brewery", breweryId).param("clientRequest", clientRequestId)
                .query((rs, n) -> map(rs)).optional();
    }

    @Override
    public boolean existsInBatch(UUID breweryId, UUID batchId, UUID measurementId) {
        return jdbc.sql("""
                SELECT 1 FROM production_measurement
                WHERE brewery_id = :brewery AND batch_id = :batch AND id = :id
                """)
                .param("brewery", breweryId).param("batch", batchId).param("id", measurementId)
                .query(Integer.class).optional().isPresent();
    }

    @Override
    public List<Measurement> findByBatch(UUID breweryId, UUID batchId) {
        return jdbc.sql("""
                SELECT id, brewery_id, batch_id, step_id, kind, measured_value, unit, temperature_c, method,
                       source, recorded_at, recorded_by, client_request_id
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
                rs.getObject("recorded_by", UUID.class),
                rs.getString("client_request_id"));
    }
}
