package br.com.brew.brassia.production.adapter.outbound.persistence;

import br.com.brew.brassia.production.application.port.outbound.LaborRepository;
import br.com.brew.brassia.production.domain.LaborEntry;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcLaborRepository implements LaborRepository {

    private final JdbcClient jdbc;

    JdbcLaborRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(LaborEntry entry) {
        jdbc.sql("""
                INSERT INTO production_labor_entry (id, brewery_id, batch_id, activity, started_at,
                        ended_at, people, recorded_by, recorded_at)
                VALUES (:id, :brewery, :batch, :activity, :startedAt, :endedAt, :people, :by, :at)
                """)
                .param("id", entry.id()).param("brewery", entry.breweryId())
                .param("batch", entry.batchId()).param("activity", entry.activity())
                .param("startedAt", Timestamp.from(entry.startedAt()))
                .param("endedAt", Timestamp.from(entry.endedAt()))
                .param("people", entry.people()).param("by", entry.recordedBy())
                .param("at", Timestamp.from(entry.recordedAt()))
                .update();
    }

    @Override
    public List<LaborEntry> findByBatch(UUID breweryId, UUID batchId) {
        return jdbc.sql("""
                SELECT id, brewery_id, batch_id, activity, started_at, ended_at, people, recorded_by,
                       recorded_at
                FROM production_labor_entry
                WHERE brewery_id = :brewery AND batch_id = :batch
                ORDER BY started_at
                """)
                .param("brewery", breweryId).param("batch", batchId)
                .query((rs, n) -> LaborEntry.reconstitute(
                        rs.getObject("id", UUID.class),
                        rs.getObject("brewery_id", UUID.class),
                        rs.getObject("batch_id", UUID.class),
                        rs.getString("activity"),
                        rs.getTimestamp("started_at").toInstant(),
                        rs.getTimestamp("ended_at").toInstant(),
                        rs.getInt("people"),
                        rs.getObject("recorded_by", UUID.class),
                        rs.getTimestamp("recorded_at").toInstant()))
                .list();
    }
}
