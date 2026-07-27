package br.com.brew.brassia.production.adapter.outbound.persistence;

import br.com.brew.brassia.production.application.port.outbound.AlertRepository;
import br.com.brew.brassia.production.domain.BatchAlert;
import br.com.brew.brassia.production.domain.BatchAlertKind;
import br.com.brew.brassia.production.domain.BatchAlertStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAlertRepository implements AlertRepository {

    private static final String COLUMNS = """
            SELECT id, brewery_id, batch_id, kind, message, planned_at, occurred_at, status, created_at,
                   created_by, confirmed_at, confirmed_by
            FROM production_batch_alert
            """;

    private final JdbcClient jdbc;

    JdbcAlertRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(BatchAlert a) {
        jdbc.sql("""
                INSERT INTO production_batch_alert (
                    id, brewery_id, batch_id, kind, message, planned_at, occurred_at, status, created_at, created_by)
                VALUES (:id, :brewery, :batch, :kind, :message, :planned, :occurred, :status, :createdAt, :createdBy)
                """)
                .param("id", a.id())
                .param("brewery", a.breweryId())
                .param("batch", a.batchId())
                .param("kind", a.kind().name())
                .param("message", a.message())
                .param("planned", a.plannedAt() == null ? null : Timestamp.from(a.plannedAt()))
                .param("occurred", a.occurredAt() == null ? null : Timestamp.from(a.occurredAt()))
                .param("status", a.status().name())
                .param("createdAt", Timestamp.from(a.createdAt()))
                .param("createdBy", a.createdBy())
                .update();
    }

    @Override
    public List<BatchAlert> findByBatch(UUID breweryId, UUID batchId) {
        return jdbc.sql(COLUMNS + " WHERE brewery_id = :brewery AND batch_id = :batch "
                        + "ORDER BY COALESCE(planned_at, created_at)")
                .param("brewery", breweryId).param("batch", batchId)
                .query((rs, n) -> map(rs))
                .list();
    }

    @Override
    public Optional<BatchAlert> findById(UUID breweryId, UUID alertId) {
        return jdbc.sql(COLUMNS + " WHERE brewery_id = :brewery AND id = :id")
                .param("brewery", breweryId).param("id", alertId)
                .query((rs, n) -> map(rs))
                .optional();
    }

    @Override
    public boolean markConfirmed(UUID breweryId, UUID alertId, Instant at, UUID by) {
        int updated = jdbc.sql("""
                UPDATE production_batch_alert
                SET status = 'CONFIRMED', confirmed_at = :at, confirmed_by = :by
                WHERE brewery_id = :brewery AND id = :id AND status = 'PENDING'
                """)
                .param("brewery", breweryId).param("id", alertId)
                .param("at", Timestamp.from(at)).param("by", by)
                .update();
        return updated > 0;
    }

    private BatchAlert map(ResultSet rs) throws SQLException {
        var planned = rs.getTimestamp("planned_at");
        var occurred = rs.getTimestamp("occurred_at");
        var confirmedAt = rs.getTimestamp("confirmed_at");
        return BatchAlert.reconstitute(
                rs.getObject("id", UUID.class),
                rs.getObject("brewery_id", UUID.class),
                rs.getObject("batch_id", UUID.class),
                BatchAlertKind.valueOf(rs.getString("kind")),
                rs.getString("message"),
                planned == null ? null : planned.toInstant(),
                occurred == null ? null : occurred.toInstant(),
                BatchAlertStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getObject("created_by", UUID.class),
                confirmedAt == null ? null : confirmedAt.toInstant(),
                rs.getObject("confirmed_by", UUID.class));
    }
}
