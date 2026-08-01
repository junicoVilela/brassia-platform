package br.com.brew.brassia.fermentation.adapter.outbound.persistence;

import br.com.brew.brassia.fermentation.application.port.outbound.ScheduleRepository;
import br.com.brew.brassia.fermentation.domain.AdvanceCondition;
import br.com.brew.brassia.fermentation.domain.FermentationSchedule;
import br.com.brew.brassia.fermentation.domain.ScheduleAction;
import br.com.brew.brassia.fermentation.domain.ScheduleStep;
import br.com.brew.brassia.fermentation.domain.ScheduleStepStatus;
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
class JdbcScheduleRepository implements ScheduleRepository {

    private static final String COLUMNS = """
            SELECT id, brewery_id, batch_id, profile_id, profile_version FROM fermentation_schedule
            """;

    private final JdbcClient jdbc;

    JdbcScheduleRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(FermentationSchedule schedule) {
        jdbc.sql("""
                INSERT INTO fermentation_schedule (id, brewery_id, batch_id, profile_id, profile_version, created_at)
                VALUES (:id, :brewery, :batch, :profile, :version, :at)
                """)
                .param("id", schedule.id())
                .param("brewery", schedule.breweryId())
                .param("batch", schedule.batchId())
                .param("profile", schedule.profileId())
                .param("version", schedule.profileVersion())
                .param("at", Timestamp.from(Instant.now()))
                .update();
        insertSteps(schedule);
    }

    @Override
    public void replaceSteps(FermentationSchedule schedule) {
        jdbc.sql("DELETE FROM fermentation_schedule_step WHERE schedule_id = :id")
                .param("id", schedule.id()).update();
        insertSteps(schedule);
    }

    private void insertSteps(FermentationSchedule schedule) {
        for (var s : schedule.ordered()) {
            jdbc.sql("""
                    INSERT INTO fermentation_schedule_step (id, schedule_id, brewery_id, step_order, name, action,
                        condition, condition_days, target_gravity, planned_start, planned_end, tolerance_hours,
                        responsible_user_id, depends_on_previous, status, executed_at, justification)
                    VALUES (:id, :schedule, :brewery, :order, :name, :action, :condition, :days, :gravity, :start,
                        :end, :tolerance, :responsible, :depends, :status, :executed, :justification)
                    """)
                    .param("id", s.id())
                    .param("schedule", schedule.id())
                    .param("brewery", schedule.breweryId())
                    .param("order", s.sequence())
                    .param("name", s.name())
                    .param("action", s.action().name())
                    .param("condition", s.condition().name())
                    .param("days", s.conditionDays())
                    .param("gravity", s.targetGravity())
                    .param("start", Timestamp.from(s.plannedStart()))
                    .param("end", Timestamp.from(s.plannedEnd()))
                    .param("tolerance", s.toleranceHours())
                    .param("responsible", s.responsibleUserId())
                    .param("depends", s.dependsOnPrevious())
                    .param("status", s.status().name())
                    .param("executed", s.executedAt() == null ? null : Timestamp.from(s.executedAt()))
                    .param("justification", s.justification())
                    .update();
        }
    }

    @Override
    public Optional<FermentationSchedule> findByBatch(UUID breweryId, UUID batchId) {
        return jdbc.sql(COLUMNS + " WHERE brewery_id = :brewery AND batch_id = :batch")
                .param("brewery", breweryId).param("batch", batchId)
                .query((rs, n) -> map(rs))
                .optional();
    }

    @Override
    public Optional<FermentationSchedule> findById(UUID breweryId, UUID scheduleId) {
        return jdbc.sql(COLUMNS + " WHERE brewery_id = :brewery AND id = :id")
                .param("brewery", breweryId).param("id", scheduleId)
                .query((rs, n) -> map(rs))
                .optional();
    }

    @Override
    public List<FermentationSchedule> findWithPendingSteps(UUID breweryId) {
        return jdbc.sql(COLUMNS + """
                 WHERE brewery_id = :brewery AND EXISTS (
                    SELECT 1 FROM fermentation_schedule_step s
                    WHERE s.schedule_id = fermentation_schedule.id AND s.status = 'PLANNED')
                """)
                .param("brewery", breweryId)
                .query((rs, n) -> map(rs))
                .list();
    }

    private FermentationSchedule map(ResultSet rs) throws SQLException {
        var id = rs.getObject("id", UUID.class);
        var breweryId = rs.getObject("brewery_id", UUID.class);
        return FermentationSchedule.reconstitute(
                id,
                breweryId,
                rs.getObject("batch_id", UUID.class),
                rs.getObject("profile_id", UUID.class),
                rs.getInt("profile_version"),
                steps(id));
    }

    private List<ScheduleStep> steps(UUID scheduleId) {
        return jdbc.sql("""
                SELECT id, step_order, name, action, condition, condition_days, target_gravity, planned_start,
                       planned_end, tolerance_hours, responsible_user_id, depends_on_previous, status, executed_at,
                       justification
                FROM fermentation_schedule_step WHERE schedule_id = :schedule ORDER BY step_order
                """)
                .param("schedule", scheduleId)
                .query((rs, n) -> {
                    var executedAt = rs.getTimestamp("executed_at");
                    return ScheduleStep.reconstitute(
                            rs.getObject("id", UUID.class),
                            rs.getInt("step_order"),
                            rs.getString("name"),
                            ScheduleAction.valueOf(rs.getString("action")),
                            AdvanceCondition.valueOf(rs.getString("condition")),
                            rs.getObject("condition_days", Integer.class),
                            rs.getBigDecimal("target_gravity"),
                            rs.getTimestamp("planned_start").toInstant(),
                            rs.getTimestamp("planned_end").toInstant(),
                            rs.getInt("tolerance_hours"),
                            rs.getObject("responsible_user_id", UUID.class),
                            rs.getBoolean("depends_on_previous"),
                            ScheduleStepStatus.valueOf(rs.getString("status")),
                            executedAt == null ? null : executedAt.toInstant(),
                            rs.getString("justification"));
                })
                .list();
    }
}
