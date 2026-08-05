package br.com.brew.brassia.traceability.adapter.outbound.persistence;

import br.com.brew.brassia.traceability.LineageSource.NodeType;
import br.com.brew.brassia.traceability.application.port.outbound.RecallRepository;
import br.com.brew.brassia.traceability.domain.Recall;
import br.com.brew.brassia.traceability.domain.RecallNotification;
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
class JdbcRecallRepository implements RecallRepository {

    private static final String COLUMNS = """
            id, brewery_id, code, node_type, node_id, origin_label, reason, status, opened_by, opened_at,
            closed_by, closed_at, closing_summary, version
            """;

    private static final String NOTIFICATION_COLUMNS = """
            id, recall_id, shipment_id, finished_lot_code, destination, contact, units, status, channel,
            note, notified_by, notified_at
            """;

    private final JdbcClient jdbc;

    JdbcRecallRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Recall recall, List<RecallNotification> notifications) {
        jdbc.sql("""
                INSERT INTO traceability_recall (id, brewery_id, code, node_type, node_id, origin_label,
                        reason, status, opened_by, opened_at, version)
                VALUES (:id, :brewery, :code, :nodeType, :nodeId, :label, :reason, :status, :openedBy,
                        :openedAt, 0)
                """)
                .param("id", recall.id())
                .param("brewery", recall.breweryId())
                .param("code", recall.code())
                .param("nodeType", recall.nodeType().name())
                .param("nodeId", recall.nodeId())
                .param("label", recall.originLabel())
                .param("reason", recall.reason())
                .param("status", recall.status().name())
                .param("openedBy", recall.openedBy())
                .param("openedAt", Timestamp.from(recall.openedAt()))
                .update();

        for (var notification : notifications) {
            jdbc.sql("""
                    INSERT INTO traceability_recall_notification (id, brewery_id, recall_id, shipment_id,
                            finished_lot_code, destination, contact, units, status)
                    VALUES (:id, :brewery, :recall, :shipment, :lotCode, :destination, :contact, :units,
                            :status)
                    """)
                    .param("id", notification.id())
                    .param("brewery", recall.breweryId())
                    .param("recall", recall.id())
                    .param("shipment", notification.shipmentId())
                    .param("lotCode", notification.finishedLotCode())
                    .param("destination", notification.destination())
                    .param("contact", notification.contact())
                    .param("units", notification.units())
                    .param("status", notification.status().name())
                    .update();
        }
    }

    @Override
    public Optional<Recall> findById(UUID breweryId, UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM traceability_recall "
                        + "WHERE brewery_id = :brewery AND id = :id")
                .param("brewery", breweryId).param("id", id)
                .query(JdbcRecallRepository::map).optional();
    }

    @Override
    public Optional<Recall> findForUpdate(UUID breweryId, UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM traceability_recall "
                        + "WHERE brewery_id = :brewery AND id = :id FOR UPDATE")
                .param("brewery", breweryId).param("id", id)
                .query(JdbcRecallRepository::map).optional();
    }

    @Override
    public List<Recall> findAll(UUID breweryId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM traceability_recall "
                        + "WHERE brewery_id = :brewery ORDER BY opened_at DESC")
                .param("brewery", breweryId).query(JdbcRecallRepository::map).list();
    }

    @Override
    public List<RecallNotification> findNotifications(UUID breweryId, UUID recallId) {
        return jdbc.sql("SELECT " + NOTIFICATION_COLUMNS + " FROM traceability_recall_notification "
                        + "WHERE brewery_id = :brewery AND recall_id = :recall ORDER BY destination")
                .param("brewery", breweryId).param("recall", recallId)
                .query(JdbcRecallRepository::mapNotification).list();
    }

    @Override
    public Optional<RecallNotification> findNotification(UUID breweryId, UUID recallId, UUID notificationId) {
        return jdbc.sql("SELECT " + NOTIFICATION_COLUMNS + " FROM traceability_recall_notification "
                        + "WHERE brewery_id = :brewery AND recall_id = :recall AND id = :id")
                .param("brewery", breweryId).param("recall", recallId).param("id", notificationId)
                .query(JdbcRecallRepository::mapNotification).optional();
    }

    @Override
    public void updateNotification(UUID breweryId, RecallNotification notification) {
        jdbc.sql("""
                UPDATE traceability_recall_notification
                SET status = :status, channel = :channel, note = :note, notified_by = :by,
                    notified_at = :at
                WHERE brewery_id = :brewery AND id = :id
                """)
                .param("status", notification.status().name())
                .param("channel", notification.channel())
                .param("note", notification.note())
                .param("by", notification.notifiedBy())
                .param("at", notification.notifiedAt() == null ? null
                        : Timestamp.from(notification.notifiedAt()))
                .param("brewery", breweryId)
                .param("id", notification.id())
                .update();
    }

    @Override
    public int countPending(UUID breweryId, UUID recallId) {
        return jdbc.sql("SELECT COUNT(*) FROM traceability_recall_notification "
                        + "WHERE brewery_id = :brewery AND recall_id = :recall AND status = 'PENDING'")
                .param("brewery", breweryId).param("recall", recallId)
                .query(Integer.class).single();
    }

    @Override
    public boolean updateStatus(Recall recall, long expectedVersion) {
        return jdbc.sql("""
                UPDATE traceability_recall
                SET status = :status, closed_by = :closedBy, closed_at = :closedAt,
                    closing_summary = :summary, version = version + 1
                WHERE brewery_id = :brewery AND id = :id AND version = :expected
                """)
                .param("status", recall.status().name())
                .param("closedBy", recall.closedBy())
                .param("closedAt", recall.closedAt() == null ? null : Timestamp.from(recall.closedAt()))
                .param("summary", recall.closingSummary())
                .param("brewery", recall.breweryId())
                .param("id", recall.id())
                .param("expected", expectedVersion)
                .update() == 1;
    }

    @Override
    public long nextSequence(UUID breweryId, int year) {
        return jdbc.sql("""
                SELECT COUNT(*) + 1 FROM traceability_recall
                WHERE brewery_id = :brewery AND EXTRACT(YEAR FROM opened_at AT TIME ZONE 'UTC') = :year
                """)
                .param("brewery", breweryId).param("year", year)
                .query(Long.class).single();
    }

    private static Recall map(ResultSet rs, int rowNum) throws SQLException {
        return Recall.reconstitute(
                rs.getObject("id", UUID.class),
                rs.getObject("brewery_id", UUID.class),
                rs.getString("code"),
                NodeType.valueOf(rs.getString("node_type")),
                rs.getObject("node_id", UUID.class),
                rs.getString("origin_label"),
                rs.getString("reason"),
                rs.getObject("opened_by", UUID.class),
                rs.getTimestamp("opened_at").toInstant(),
                Recall.RecallStatus.valueOf(rs.getString("status")),
                rs.getObject("closed_by", UUID.class),
                instantOrNull(rs.getTimestamp("closed_at")),
                rs.getString("closing_summary"),
                rs.getLong("version"));
    }

    private static RecallNotification mapNotification(ResultSet rs, int rowNum) throws SQLException {
        return RecallNotification.reconstitute(
                rs.getObject("id", UUID.class),
                rs.getObject("recall_id", UUID.class),
                rs.getObject("shipment_id", UUID.class),
                rs.getString("finished_lot_code"),
                rs.getString("destination"),
                rs.getString("contact"),
                rs.getInt("units"),
                RecallNotification.NotificationStatus.valueOf(rs.getString("status")),
                rs.getString("channel"),
                rs.getString("note"),
                rs.getObject("notified_by", UUID.class),
                instantOrNull(rs.getTimestamp("notified_at")));
    }

    private static Instant instantOrNull(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
