package br.com.brew.brassia.traceability.adapter.outbound.persistence;

import br.com.brew.brassia.traceability.LineageSource.NodeType;
import br.com.brew.brassia.traceability.application.port.outbound.DrillRepository;
import br.com.brew.brassia.traceability.domain.RecallDrill;
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
class JdbcDrillRepository implements DrillRepository {

    private static final String COLUMNS = """
            id, brewery_id, code, node_type, node_id, origin_label, note, status, started_by, started_at,
            finished_by, finished_at, units_in_scope, units_located, destinations_reached, gaps_found,
            summary, corrective_actions, non_conformity_id
            """;

    private final JdbcClient jdbc;

    JdbcDrillRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(RecallDrill drill) {
        jdbc.sql("""
                INSERT INTO traceability_recall_drill (id, brewery_id, code, node_type, node_id,
                        origin_label, note, status, started_by, started_at)
                VALUES (:id, :brewery, :code, :nodeType, :nodeId, :label, :note, :status, :by, :at)
                """)
                .param("id", drill.id())
                .param("brewery", drill.breweryId())
                .param("code", drill.code())
                .param("nodeType", drill.nodeType().name())
                .param("nodeId", drill.nodeId())
                .param("label", drill.originLabel())
                .param("note", drill.note())
                .param("status", drill.status().name())
                .param("by", drill.startedBy())
                .param("at", Timestamp.from(drill.startedAt()))
                .update();
    }

    @Override
    public Optional<RecallDrill> findById(UUID breweryId, UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM traceability_recall_drill "
                        + "WHERE brewery_id = :brewery AND id = :id")
                .param("brewery", breweryId).param("id", id)
                .query(JdbcDrillRepository::map).optional();
    }

    @Override
    public Optional<RecallDrill> findForUpdate(UUID breweryId, UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM traceability_recall_drill "
                        + "WHERE brewery_id = :brewery AND id = :id FOR UPDATE")
                .param("brewery", breweryId).param("id", id)
                .query(JdbcDrillRepository::map).optional();
    }

    @Override
    public List<RecallDrill> findAll(UUID breweryId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM traceability_recall_drill "
                        + "WHERE brewery_id = :brewery ORDER BY started_at DESC")
                .param("brewery", breweryId).query(JdbcDrillRepository::map).list();
    }

    /** Só o encerramento escreve resultado; simulado que corre não tem número gravado. */
    @Override
    public void finish(RecallDrill drill) {
        jdbc.sql("""
                UPDATE traceability_recall_drill
                SET status = :status, finished_by = :by, finished_at = :at, units_in_scope = :scope,
                    units_located = :located, destinations_reached = :reached, gaps_found = :gaps,
                    summary = :summary, corrective_actions = :actions, non_conformity_id = :nc
                WHERE brewery_id = :brewery AND id = :id AND status = 'RUNNING'
                """)
                .param("status", drill.status().name())
                .param("by", drill.finishedBy())
                .param("at", Timestamp.from(drill.finishedAt()))
                .param("scope", drill.unitsInScope())
                .param("located", drill.unitsLocated())
                .param("reached", drill.destinationsReached())
                .param("gaps", drill.gapsFound())
                .param("summary", drill.summary())
                .param("actions", drill.correctiveActions())
                .param("nc", drill.nonConformityId().orElse(null))
                .param("brewery", drill.breweryId())
                .param("id", drill.id())
                .update();
    }

    @Override
    public long nextSequence(UUID breweryId, int year) {
        return jdbc.sql("""
                SELECT COUNT(*) + 1 FROM traceability_recall_drill
                WHERE brewery_id = :brewery AND EXTRACT(YEAR FROM started_at AT TIME ZONE 'UTC') = :year
                """)
                .param("brewery", breweryId).param("year", year)
                .query(Long.class).single();
    }

    private static RecallDrill map(ResultSet rs, int rowNum) throws SQLException {
        return RecallDrill.reconstitute(
                rs.getObject("id", UUID.class),
                rs.getObject("brewery_id", UUID.class),
                rs.getString("code"),
                NodeType.valueOf(rs.getString("node_type")),
                rs.getObject("node_id", UUID.class),
                rs.getString("origin_label"),
                rs.getString("note"),
                rs.getObject("started_by", UUID.class),
                rs.getTimestamp("started_at").toInstant(),
                RecallDrill.DrillStatus.valueOf(rs.getString("status")),
                rs.getObject("finished_by", UUID.class),
                instantOrNull(rs.getTimestamp("finished_at")),
                intOrNull(rs, "units_in_scope"),
                intOrNull(rs, "units_located"),
                intOrNull(rs, "destinations_reached"),
                intOrNull(rs, "gaps_found"),
                rs.getString("summary"),
                rs.getString("corrective_actions"),
                rs.getObject("non_conformity_id", java.util.UUID.class));
    }

    private static Integer intOrNull(ResultSet rs, String column) throws SQLException {
        var value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Instant instantOrNull(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
