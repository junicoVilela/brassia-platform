package br.com.brew.brassia.traceability.adapter.outbound.persistence;

import br.com.brew.brassia.traceability.LineageSource.NodeType;
import br.com.brew.brassia.traceability.application.port.outbound.QuarantineRepository;
import br.com.brew.brassia.traceability.domain.Quarantine;
import br.com.brew.brassia.traceability.domain.QuarantineStatus;
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
class JdbcQuarantineRepository implements QuarantineRepository {

    private static final String COLUMNS = """
            id, brewery_id, node_type, node_id, origin_label, reason, opened_by, opened_at, status,
            released_by, released_at, release_justification, version
            """;

    private final JdbcClient jdbc;

    JdbcQuarantineRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Quarantine quarantine) {
        jdbc.sql("""
                INSERT INTO traceability_quarantine (id, brewery_id, node_type, node_id, origin_label, reason,
                        opened_by, opened_at, status, version)
                VALUES (:id, :brewery, :nodeType, :nodeId, :label, :reason, :openedBy, :openedAt, :status, 0)
                """)
                .param("id", quarantine.id())
                .param("brewery", quarantine.breweryId())
                .param("nodeType", quarantine.nodeType().name())
                .param("nodeId", quarantine.nodeId())
                .param("label", quarantine.originLabel())
                .param("reason", quarantine.reason())
                .param("openedBy", quarantine.openedBy())
                .param("openedAt", Timestamp.from(quarantine.openedAt()))
                .param("status", quarantine.status().name())
                .update();
    }

    @Override
    public Optional<Quarantine> findById(UUID breweryId, UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM traceability_quarantine "
                        + "WHERE brewery_id = :brewery AND id = :id")
                .param("brewery", breweryId).param("id", id)
                .query(JdbcQuarantineRepository::map).optional();
    }

    @Override
    public Optional<Quarantine> findForUpdate(UUID breweryId, UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM traceability_quarantine "
                        + "WHERE brewery_id = :brewery AND id = :id FOR UPDATE")
                .param("brewery", breweryId).param("id", id)
                .query(JdbcQuarantineRepository::map).optional();
    }

    @Override
    public Optional<Quarantine> findOpenFor(UUID breweryId, NodeType type, UUID nodeId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM traceability_quarantine "
                        + "WHERE brewery_id = :brewery AND node_type = :type AND node_id = :node "
                        + "AND status = 'OPEN'")
                .param("brewery", breweryId).param("type", type.name()).param("node", nodeId)
                .query(JdbcQuarantineRepository::map).optional();
    }

    @Override
    public List<Quarantine> findOpen(UUID breweryId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM traceability_quarantine "
                        + "WHERE brewery_id = :brewery AND status = 'OPEN' ORDER BY opened_at DESC")
                .param("brewery", breweryId).query(JdbcQuarantineRepository::map).list();
    }

    @Override
    public List<Quarantine> findAll(UUID breweryId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM traceability_quarantine "
                        + "WHERE brewery_id = :brewery ORDER BY opened_at DESC")
                .param("brewery", breweryId).query(JdbcQuarantineRepository::map).list();
    }

    @Override
    public boolean updateStatus(Quarantine quarantine, long expectedVersion) {
        return jdbc.sql("""
                UPDATE traceability_quarantine
                SET status = :status, released_by = :releasedBy, released_at = :releasedAt,
                    release_justification = :justification, version = version + 1
                WHERE brewery_id = :brewery AND id = :id AND version = :expected
                """)
                .param("status", quarantine.status().name())
                .param("releasedBy", quarantine.releasedBy())
                .param("releasedAt", quarantine.releasedAt() == null ? null
                        : Timestamp.from(quarantine.releasedAt()))
                .param("justification", quarantine.releaseJustification())
                .param("brewery", quarantine.breweryId())
                .param("id", quarantine.id())
                .param("expected", expectedVersion)
                .update() == 1;
    }

    private static Quarantine map(ResultSet rs, int rowNum) throws SQLException {
        return Quarantine.reconstitute(
                rs.getObject("id", UUID.class),
                rs.getObject("brewery_id", UUID.class),
                NodeType.valueOf(rs.getString("node_type")),
                rs.getObject("node_id", UUID.class),
                rs.getString("origin_label"),
                rs.getString("reason"),
                rs.getObject("opened_by", UUID.class),
                rs.getTimestamp("opened_at").toInstant(),
                QuarantineStatus.valueOf(rs.getString("status")),
                rs.getObject("released_by", UUID.class),
                instantOrNull(rs.getTimestamp("released_at")),
                rs.getString("release_justification"),
                rs.getLong("version"));
    }

    private static Instant instantOrNull(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
