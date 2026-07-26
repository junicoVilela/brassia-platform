package br.com.brew.brassia.planning.adapter.outbound.persistence;

import br.com.brew.brassia.planning.application.port.outbound.ScheduleEntryRepository;
import br.com.brew.brassia.planning.domain.ScheduleEntry;
import br.com.brew.brassia.planning.domain.ScheduleEntryId;
import br.com.brew.brassia.planning.domain.ScheduleStatus;
import br.com.brew.brassia.planning.domain.ScheduleWindow;
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
class JdbcScheduleEntryRepository implements ScheduleEntryRepository {

    private final JdbcClient jdbc;

    JdbcScheduleEntryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(ScheduleEntry e) {
        // Uma violação da exclusion constraint (ex_planning_schedule_no_overlap) sobe como
        // DataIntegrityViolationException e é traduzida para 409 na camada de aplicação
        // (PlanningConfiguration), evitando a re-tradução do @Repository sobre exceções próprias.
        jdbc.sql("""
                INSERT INTO planning_schedule_entry (
                    id, brewery_id, recipe_id, equipment_id, assigned_user_id, planned_volume_liters,
                    scheduled_start, scheduled_end, status, version, created_at)
                VALUES (:id, :brewery, :recipe, :equipment, :user, :volume,
                        :start, :end, :status, :version, :at)
                """)
                .param("id", e.id().value())
                .param("brewery", e.breweryId())
                .param("recipe", e.recipeId())
                .param("equipment", e.equipmentId())
                .param("user", e.assignedUserId())
                .param("volume", e.plannedVolumeLiters())
                .param("start", Timestamp.from(e.window().start()))
                .param("end", Timestamp.from(e.window().end()))
                .param("status", e.status().name())
                .param("version", e.version())
                .param("at", Timestamp.from(Instant.now()))
                .update();
    }

    @Override
    public List<Conflict> findEquipmentConflicts(UUID breweryId, UUID equipmentId, Instant start, Instant end) {
        return jdbc.sql("""
                SELECT id, scheduled_start, scheduled_end
                FROM planning_schedule_entry
                WHERE brewery_id = :brewery AND equipment_id = :equipment AND status = 'PLANNED'
                  AND scheduled_start < :end AND :start < scheduled_end
                ORDER BY scheduled_start
                """)
                .param("brewery", breweryId)
                .param("equipment", equipmentId)
                .param("start", Timestamp.from(start))
                .param("end", Timestamp.from(end))
                .query((rs, n) -> new Conflict(
                        rs.getObject("id", UUID.class),
                        rs.getTimestamp("scheduled_start").toInstant(),
                        rs.getTimestamp("scheduled_end").toInstant()))
                .list();
    }

    @Override
    public List<ScheduleEntry> findBetween(UUID breweryId, Instant from, Instant to) {
        return jdbc.sql("""
                SELECT id, brewery_id, recipe_id, equipment_id, assigned_user_id, planned_volume_liters,
                       scheduled_start, scheduled_end, status, version
                FROM planning_schedule_entry
                WHERE brewery_id = :brewery AND scheduled_start < :to AND :from < scheduled_end
                ORDER BY scheduled_start
                """)
                .param("brewery", breweryId)
                .param("from", Timestamp.from(from))
                .param("to", Timestamp.from(to))
                .query((rs, n) -> map(rs))
                .list();
    }

    @Override
    public Optional<ScheduleEntry> findById(UUID breweryId, UUID id) {
        return jdbc.sql("""
                SELECT id, brewery_id, recipe_id, equipment_id, assigned_user_id, planned_volume_liters,
                       scheduled_start, scheduled_end, status, version
                FROM planning_schedule_entry
                WHERE brewery_id = :brewery AND id = :id
                """)
                .param("brewery", breweryId)
                .param("id", id)
                .query((rs, n) -> map(rs))
                .optional();
    }

    private static ScheduleEntry map(ResultSet rs) throws SQLException {
        return ScheduleEntry.reconstitute(
                new ScheduleEntryId(rs.getObject("id", UUID.class)),
                rs.getObject("brewery_id", UUID.class),
                rs.getObject("recipe_id", UUID.class),
                rs.getObject("equipment_id", UUID.class),
                rs.getObject("assigned_user_id", UUID.class),
                rs.getBigDecimal("planned_volume_liters"),
                new ScheduleWindow(
                        rs.getTimestamp("scheduled_start").toInstant(),
                        rs.getTimestamp("scheduled_end").toInstant()),
                ScheduleStatus.valueOf(rs.getString("status")),
                rs.getLong("version"));
    }
}
