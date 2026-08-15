package br.com.brew.brassia.quality.adapter.outbound.persistence;

import br.com.brew.brassia.quality.application.port.outbound.FrequencySweepRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcFrequencySweepRepository implements FrequencySweepRepository {

    private final JdbcClient jdbc;

    JdbcFrequencySweepRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<UUID> breweriesWithPublishedPlans() {
        return jdbc.sql("""
                SELECT DISTINCT brewery_id FROM quality_control_plan WHERE status = 'PUBLISHED'
                """)
                .query(UUID.class).list();
    }

    @Override
    public List<HourlyPoint> hourlyPointsFor(UUID breweryId, UUID recipeId) {
        return jdbc.sql("""
                SELECT p.id, p.parameter, p.every_hours, p.severity, p.critical
                FROM quality_control_point p
                JOIN quality_control_plan c ON c.id = p.plan_id
                WHERE c.brewery_id = :brewery AND c.status = 'PUBLISHED'
                  AND p.frequency_kind = 'PER_HOURS'
                  AND (c.recipe_id IS NULL OR c.recipe_id = :recipe)
                ORDER BY p.parameter
                """)
                .param("brewery", breweryId).param("recipe", recipeId)
                .query((rs, n) -> new HourlyPoint(rs.getObject("id", UUID.class), rs.getString("parameter"),
                        rs.getInt("every_hours"), rs.getString("severity"), rs.getBoolean("critical")))
                .list();
    }

    @Override
    public Instant lastMeasuredAt(UUID breweryId, UUID pointId, UUID batchId) {
        return jdbc.sql("""
                SELECT max(measured_at) FROM quality_measurement
                WHERE brewery_id = :brewery AND point_id = :point AND batch_id = :batch
                """)
                .param("brewery", breweryId).param("point", pointId).param("batch", batchId)
                .query(Timestamp.class).optional()
                .map(Timestamp::toInstant)
                .orElse(null);
    }

    @Override
    public boolean recordAlert(UUID breweryId, UUID pointId, UUID batchId, Instant missedWindowAt,
            Instant at) {
        try {
            jdbc.sql("""
                    INSERT INTO quality_frequency_alert (id, brewery_id, point_id, batch_id,
                            missed_window_at, alerted_at)
                    VALUES (:id, :brewery, :point, :batch, :missed, :at)
                    """)
                    .param("id", UUID.randomUUID()).param("brewery", breweryId).param("point", pointId)
                    .param("batch", batchId).param("missed", Timestamp.from(missedWindowAt))
                    .param("at", Timestamp.from(at))
                    .update();
            return true;
        } catch (DuplicateKeyException alreadyAlerted) {
            // Esta janela já foi avisada. Repetir encheria a central do lote com o mesmo aviso a cada
            // passagem do agendador — e central que repete é central que ninguém lê.
            return false;
        }
    }
}
