package br.com.brew.brassia.distribution.adapter.outbound.persistence;

import br.com.brew.brassia.distribution.application.port.outbound.SyncRepository;
import br.com.brew.brassia.distribution.domain.OfflineOperation;
import br.com.brew.brassia.distribution.domain.SyncStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSyncRepository implements SyncRepository {

    private static final String COLUMNS = """
            client_operation_id, device_id, load_id, stop_id, occurred_at, received_at, sequence,
            status, result_id, reason
            """;

    private final JdbcClient jdbc;

    JdbcSyncRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(UUID breweryId, OfflineOperation op) {
        jdbc.sql("""
                INSERT INTO distribution_sync_operation (id, brewery_id, client_operation_id,
                        device_id, load_id, stop_id, occurred_at, received_at, sequence, status,
                        result_id, reason)
                VALUES (:id, :brewery, :client, :device, :load, :stop, :occurred, :received,
                        :sequence, :status, :result, :reason)
                """)
                .param("id", UUID.randomUUID()).param("brewery", breweryId)
                .param("client", op.clientOperationId()).param("device", op.deviceId())
                .param("load", op.loadId()).param("stop", op.stopId())
                .param("occurred", Timestamp.from(op.occurredAt()))
                .param("received", Timestamp.from(op.receivedAt()))
                .param("sequence", op.sequence()).param("status", op.status().name())
                .param("result", op.resultId().orElse(null))
                .param("reason", op.reason().orElse(null))
                .update();
    }

    @Override
    public Optional<OfflineOperation> find(UUID breweryId, UUID deviceId, UUID clientOperationId) {
        return jdbc.sql("SELECT " + COLUMNS + """
                 FROM distribution_sync_operation
                WHERE brewery_id = :brewery AND device_id = :device
                  AND client_operation_id = :client
                """)
                .param("brewery", breweryId).param("device", deviceId)
                .param("client", clientOperationId)
                .query(JdbcSyncRepository::map).optional();
    }

    @Override
    public List<OfflineOperation> conflicts(UUID breweryId) {
        return jdbc.sql("SELECT " + COLUMNS + """
                 FROM distribution_sync_operation
                WHERE brewery_id = :brewery AND status = 'CONFLICTED'
                ORDER BY received_at
                """)
                .param("brewery", breweryId).query(JdbcSyncRepository::map).list();
    }

    @Override
    public List<OfflineOperation> ofLoad(UUID breweryId, UUID loadId) {
        return jdbc.sql("SELECT " + COLUMNS + """
                 FROM distribution_sync_operation
                WHERE brewery_id = :brewery AND load_id = :load
                ORDER BY sequence
                """)
                .param("brewery", breweryId).param("load", loadId)
                .query(JdbcSyncRepository::map).list();
    }

    private static OfflineOperation map(ResultSet rs, int row) throws SQLException {
        return OfflineOperation.reconstitute(rs.getObject("client_operation_id", UUID.class),
                rs.getObject("device_id", UUID.class), rs.getObject("load_id", UUID.class),
                rs.getObject("stop_id", UUID.class), rs.getTimestamp("occurred_at").toInstant(),
                rs.getTimestamp("received_at").toInstant(), rs.getInt("sequence"),
                SyncStatus.valueOf(rs.getString("status")), rs.getObject("result_id", UUID.class),
                rs.getString("reason"));
    }
}
