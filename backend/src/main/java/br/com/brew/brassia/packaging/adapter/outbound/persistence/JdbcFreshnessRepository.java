package br.com.brew.brassia.packaging.adapter.outbound.persistence;

import br.com.brew.brassia.packaging.application.port.outbound.FreshnessRepository;
import br.com.brew.brassia.packaging.domain.FreshnessRecord;
import br.com.brew.brassia.packaging.domain.OxygenMeasurement;
import br.com.brew.brassia.packaging.domain.ShelfLifePolicy;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcFreshnessRepository implements FreshnessRepository {

    private static final String COLUMNS = """
            SELECT plan_id, brewery_id, packaged_on, dissolved_oxygen_ppb, total_package_oxygen_ppb,
                   purge_method, purge_verified, seal_check_method, seal_check_passed,
                   recommended_shelf_life_days, recommended_best_before, recorded_by, recorded_at,
                   override_shelf_life_days, override_best_before, override_reason, overridden_by,
                   overridden_at, version
            FROM packaging_freshness
            """;

    private final JdbcClient jdbc;

    JdbcFreshnessRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(FreshnessRecord r) {
        var m = r.measurement();
        // Remedir substitui o registro do plano; o override é zerado porque a evidência mudou.
        jdbc.sql("""
                INSERT INTO packaging_freshness (plan_id, brewery_id, packaged_on, dissolved_oxygen_ppb,
                    total_package_oxygen_ppb, purge_method, purge_verified, seal_check_method,
                    seal_check_passed, recommended_shelf_life_days, recommended_best_before, recorded_by,
                    recorded_at, version)
                VALUES (:plan, :brewery, :packagedOn, :dissolved, :total, :purgeMethod, :purgeVerified,
                    :sealMethod, :sealPassed, :recDays, :recBestBefore, :by, :at, 0)
                ON CONFLICT (plan_id) DO UPDATE SET
                    packaged_on = EXCLUDED.packaged_on,
                    dissolved_oxygen_ppb = EXCLUDED.dissolved_oxygen_ppb,
                    total_package_oxygen_ppb = EXCLUDED.total_package_oxygen_ppb,
                    purge_method = EXCLUDED.purge_method,
                    purge_verified = EXCLUDED.purge_verified,
                    seal_check_method = EXCLUDED.seal_check_method,
                    seal_check_passed = EXCLUDED.seal_check_passed,
                    recommended_shelf_life_days = EXCLUDED.recommended_shelf_life_days,
                    recommended_best_before = EXCLUDED.recommended_best_before,
                    recorded_by = EXCLUDED.recorded_by,
                    recorded_at = EXCLUDED.recorded_at,
                    override_shelf_life_days = NULL,
                    override_best_before = NULL,
                    override_reason = NULL,
                    overridden_by = NULL,
                    overridden_at = NULL,
                    version = packaging_freshness.version + 1
                """)
                .param("plan", r.planId())
                .param("brewery", r.breweryId())
                .param("packagedOn", Date.valueOf(r.packagedOn()))
                .param("dissolved", m.dissolvedOxygenPpb())
                .param("total", m.totalPackageOxygenPpb())
                .param("purgeMethod", m.purgeMethod())
                .param("purgeVerified", m.purgeVerified())
                .param("sealMethod", m.sealCheckMethod())
                .param("sealPassed", m.sealCheckPassed())
                .param("recDays", r.recommendedShelfLifeDays())
                .param("recBestBefore", r.recommendedBestBefore() == null
                        ? null : Date.valueOf(r.recommendedBestBefore()))
                .param("by", r.recordedBy())
                .param("at", Timestamp.from(r.recordedAt()))
                .update();
    }

    @Override
    public Optional<FreshnessRecord> findByPlan(UUID breweryId, UUID planId) {
        return load(breweryId, planId, "");
    }

    @Override
    public Optional<FreshnessRecord> findForUpdate(UUID breweryId, UUID planId) {
        return load(breweryId, planId, " FOR UPDATE");
    }

    private Optional<FreshnessRecord> load(UUID breweryId, UUID planId, String lock) {
        return jdbc.sql(COLUMNS + " WHERE brewery_id = :brewery AND plan_id = :plan" + lock)
                .param("brewery", breweryId).param("plan", planId)
                .query((rs, n) -> map(rs))
                .optional();
    }

    @Override
    public boolean updateOverride(FreshnessRecord r, long expectedVersion) {
        return jdbc.sql("""
                UPDATE packaging_freshness
                SET override_shelf_life_days = :days, override_best_before = :bestBefore,
                    override_reason = :reason, overridden_by = :by, overridden_at = :at,
                    version = version + 1
                WHERE plan_id = :plan AND brewery_id = :brewery AND version = :version
                """)
                .param("days", r.overrideShelfLifeDays())
                .param("bestBefore", r.overrideBestBefore() == null ? null : Date.valueOf(r.overrideBestBefore()))
                .param("reason", r.overrideReason())
                .param("by", r.overriddenBy())
                .param("at", r.overriddenAt() == null ? null : Timestamp.from(r.overriddenAt()))
                .param("plan", r.planId())
                .param("brewery", r.breweryId())
                .param("version", expectedVersion)
                .update() == 1;
    }

    @Override
    public Optional<ShelfLifePolicy> findPolicy(UUID breweryId) {
        var fallback = jdbc.sql("SELECT fallback_days FROM packaging_shelf_life_policy WHERE brewery_id = :brewery")
                .param("brewery", breweryId)
                .query(Integer.class)
                .optional();
        if (fallback.isEmpty()) {
            return Optional.empty();
        }
        var tiers = jdbc.sql("""
                SELECT max_tpo_ppb, shelf_life_days FROM packaging_shelf_life_tier
                WHERE brewery_id = :brewery ORDER BY max_tpo_ppb
                """)
                .param("brewery", breweryId)
                .query((rs, n) -> new ShelfLifePolicy.Tier(
                        rs.getBigDecimal("max_tpo_ppb"), rs.getInt("shelf_life_days")))
                .list();
        return tiers.isEmpty() ? Optional.empty() : Optional.of(new ShelfLifePolicy(tiers, fallback.get()));
    }

    @Override
    public void savePolicy(UUID breweryId, ShelfLifePolicy policy) {
        jdbc.sql("""
                INSERT INTO packaging_shelf_life_policy (brewery_id, fallback_days)
                VALUES (:brewery, :fallback)
                ON CONFLICT (brewery_id) DO UPDATE SET fallback_days = EXCLUDED.fallback_days
                """)
                .param("brewery", breweryId).param("fallback", policy.fallbackDays())
                .update();
        // As faixas são substituídas por inteiro: política parcial recomendaria com regra antiga.
        jdbc.sql("DELETE FROM packaging_shelf_life_tier WHERE brewery_id = :brewery")
                .param("brewery", breweryId)
                .update();
        for (var tier : policy.tiers()) {
            jdbc.sql("""
                    INSERT INTO packaging_shelf_life_tier (brewery_id, max_tpo_ppb, shelf_life_days)
                    VALUES (:brewery, :tpo, :days)
                    """)
                    .param("brewery", breweryId)
                    .param("tpo", tier.maxTpoPpb())
                    .param("days", tier.shelfLifeDays())
                    .update();
        }
    }

    private FreshnessRecord map(ResultSet rs) throws SQLException {
        var measurement = new OxygenMeasurement(
                rs.getBigDecimal("dissolved_oxygen_ppb"),
                rs.getBigDecimal("total_package_oxygen_ppb"),
                rs.getString("purge_method"),
                rs.getBoolean("purge_verified"),
                rs.getString("seal_check_method"),
                rs.getBoolean("seal_check_passed"));
        var overriddenAt = rs.getTimestamp("overridden_at");
        return FreshnessRecord.reconstitute(
                rs.getObject("plan_id", UUID.class),
                rs.getObject("brewery_id", UUID.class),
                rs.getObject("packaged_on", LocalDate.class),
                measurement,
                rs.getObject("recommended_shelf_life_days", Integer.class),
                rs.getObject("recommended_best_before", LocalDate.class),
                rs.getObject("recorded_by", UUID.class),
                rs.getTimestamp("recorded_at").toInstant(),
                rs.getObject("override_shelf_life_days", Integer.class),
                rs.getObject("override_best_before", LocalDate.class),
                rs.getString("override_reason"),
                rs.getObject("overridden_by", UUID.class),
                overriddenAt == null ? null : overriddenAt.toInstant(),
                rs.getLong("version"));
    }
}
