package br.com.brew.brassia.fermentation.adapter.outbound.persistence;

import br.com.brew.brassia.fermentation.application.port.outbound.ProfileRepository;
import br.com.brew.brassia.fermentation.domain.AdvanceCondition;
import br.com.brew.brassia.fermentation.domain.FermentationProfile;
import br.com.brew.brassia.fermentation.domain.FermentationStage;
import br.com.brew.brassia.fermentation.domain.FgStabilityPolicy;
import br.com.brew.brassia.fermentation.domain.ProfileId;
import br.com.brew.brassia.fermentation.domain.ProfileStatus;
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
class JdbcProfileRepository implements ProfileRepository {

    private static final String COLUMNS = """
            SELECT id, brewery_id, code, name, version, status, stability_window_hours,
                   stability_min_readings, stability_tolerance_sg
            FROM fermentation_profile
            """;

    private final JdbcClient jdbc;

    JdbcProfileRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(FermentationProfile p) {
        jdbc.sql("""
                INSERT INTO fermentation_profile (id, brewery_id, code, name, version, status, created_at,
                    stability_window_hours, stability_min_readings, stability_tolerance_sg)
                VALUES (:id, :brewery, :code, :name, :version, :status, :at, :window, :minReadings, :tolerance)
                """)
                .param("id", p.id().value())
                .param("brewery", p.breweryId())
                .param("code", p.code())
                .param("name", p.name())
                .param("version", p.version())
                .param("status", p.status().name())
                .param("at", Timestamp.from(Instant.now()))
                .param("window", p.stability().windowHours())
                .param("minReadings", p.stability().minReadings())
                .param("tolerance", p.stability().toleranceSg())
                .update();
        insertStages(p);
    }

    @Override
    public void update(FermentationProfile p) {
        jdbc.sql("""
                UPDATE fermentation_profile
                SET name = :name, stability_window_hours = :window, stability_min_readings = :minReadings,
                    stability_tolerance_sg = :tolerance
                WHERE id = :id AND brewery_id = :brewery
                """)
                .param("name", p.name())
                .param("window", p.stability().windowHours())
                .param("minReadings", p.stability().minReadings())
                .param("tolerance", p.stability().toleranceSg())
                .param("id", p.id().value()).param("brewery", p.breweryId())
                .update();
        jdbc.sql("DELETE FROM fermentation_profile_stage WHERE profile_id = :id")
                .param("id", p.id().value()).update();
        insertStages(p);
    }

    private void insertStages(FermentationProfile p) {
        for (var s : p.stages()) {
            jdbc.sql("""
                    INSERT INTO fermentation_profile_stage (id, profile_id, brewery_id, stage_order, name,
                        target_temp_c, ramp_hours, pressure_psi, condition, condition_days, target_gravity,
                        requires_confirmation)
                    VALUES (:id, :profile, :brewery, :seq, :name, :temp, :ramp, :pressure, :condition, :days,
                        :gravity, :confirm)
                    """)
                    .param("id", s.id())
                    .param("profile", p.id().value())
                    .param("brewery", p.breweryId())
                    .param("seq", s.sequence())
                    .param("name", s.name())
                    .param("temp", s.targetTempC())
                    .param("ramp", s.rampHours())
                    .param("pressure", s.pressurePsi())
                    .param("condition", s.condition().name())
                    .param("days", s.conditionDays())
                    .param("gravity", s.targetGravity())
                    .param("confirm", s.requiresConfirmation())
                    .update();
        }
    }

    @Override
    public Optional<FermentationProfile> findById(UUID breweryId, UUID profileId) {
        return jdbc.sql(COLUMNS + " WHERE brewery_id = :brewery AND id = :id")
                .param("brewery", breweryId).param("id", profileId)
                .query((rs, n) -> map(rs))
                .optional();
    }

    @Override
    public Optional<FermentationProfile> findLatestByCode(UUID breweryId, String code) {
        return jdbc.sql(COLUMNS + " WHERE brewery_id = :brewery AND code = :code ORDER BY version DESC LIMIT 1")
                .param("brewery", breweryId).param("code", code)
                .query((rs, n) -> map(rs))
                .optional();
    }

    @Override
    public List<FermentationProfile> findAll(UUID breweryId) {
        return jdbc.sql(COLUMNS + " WHERE brewery_id = :brewery ORDER BY code, version")
                .param("brewery", breweryId)
                .query((rs, n) -> map(rs))
                .list();
    }

    @Override
    public boolean markPublished(UUID breweryId, UUID profileId) {
        int updated = jdbc.sql("""
                UPDATE fermentation_profile SET status = 'PUBLISHED'
                WHERE brewery_id = :brewery AND id = :id AND status = 'DRAFT'
                """)
                .param("brewery", breweryId).param("id", profileId)
                .update();
        return updated > 0;
    }

    private FermentationProfile map(ResultSet rs) throws SQLException {
        var id = rs.getObject("id", UUID.class);
        var breweryId = rs.getObject("brewery_id", UUID.class);
        return FermentationProfile.reconstitute(
                new ProfileId(id),
                breweryId,
                rs.getString("code"),
                rs.getString("name"),
                rs.getInt("version"),
                ProfileStatus.valueOf(rs.getString("status")),
                stages(breweryId, id),
                new FgStabilityPolicy(
                        rs.getInt("stability_window_hours"),
                        rs.getInt("stability_min_readings"),
                        rs.getBigDecimal("stability_tolerance_sg")));
    }

    private List<FermentationStage> stages(UUID breweryId, UUID profileId) {
        return jdbc.sql("""
                SELECT id, stage_order, name, target_temp_c, ramp_hours, pressure_psi, condition, condition_days,
                       target_gravity, requires_confirmation
                FROM fermentation_profile_stage
                WHERE brewery_id = :brewery AND profile_id = :profile ORDER BY stage_order
                """)
                .param("brewery", breweryId).param("profile", profileId)
                .query((rs, n) -> new FermentationStage(
                        rs.getObject("id", UUID.class),
                        rs.getInt("stage_order"),
                        rs.getString("name"),
                        rs.getBigDecimal("target_temp_c"),
                        rs.getObject("ramp_hours", Integer.class),
                        rs.getBigDecimal("pressure_psi"),
                        AdvanceCondition.valueOf(rs.getString("condition")),
                        rs.getObject("condition_days", Integer.class),
                        rs.getBigDecimal("target_gravity"),
                        rs.getBoolean("requires_confirmation")))
                .list();
    }
}
