package br.com.brew.brassia.quality.adapter.outbound.persistence;

import br.com.brew.brassia.quality.application.port.outbound.ControlPlanRepository;
import br.com.brew.brassia.quality.domain.ControlPlan;
import br.com.brew.brassia.quality.domain.ControlPlanStatus;
import br.com.brew.brassia.quality.domain.ControlPoint;
import br.com.brew.brassia.quality.domain.Frequency;
import br.com.brew.brassia.quality.domain.FrequencyKind;
import br.com.brew.brassia.quality.domain.ProcessStage;
import br.com.brew.brassia.quality.domain.Severity;
import br.com.brew.brassia.quality.domain.SpecLimits;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Cabeçalho e pontos são gravados juntos: o plano só faz sentido com os seus pontos, e ler um sem
 * os outros permitiria julgar contra uma faixa incompleta.
 *
 * <p>A regravação dos pontos apaga e reinsere. Só rascunho é regravado — publicado é imutável, e
 * medições apontam para o ponto pela FK, então nenhuma linha referenciada é removida.
 */
@Repository
class JdbcControlPlanRepository implements ControlPlanRepository {

    private static final String PLAN_COLUMNS = """
            SELECT id, brewery_id, code, name, recipe_id, stage, status, version, lock_version
            FROM quality_control_plan
            """;

    private final JdbcClient jdbc;

    JdbcControlPlanRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(ControlPlan plan) {
        jdbc.sql("""
                INSERT INTO quality_control_plan (id, brewery_id, code, name, recipe_id, stage, status,
                    version, lock_version)
                VALUES (:id, :brewery, :code, :name, :recipe, :stage, :status, :version, 0)
                """)
                .param("id", plan.id()).param("brewery", plan.breweryId()).param("code", plan.code())
                .param("name", plan.name()).param("recipe", plan.recipeId().orElse(null))
                .param("stage", plan.stage().name()).param("status", plan.status().name())
                .param("version", plan.version())
                .update();
        insertPoints(plan);
    }

    @Override
    public void update(ControlPlan plan) {
        jdbc.sql("""
                UPDATE quality_control_plan
                SET name = :name, recipe_id = :recipe, stage = :stage, status = :status,
                    lock_version = lock_version + 1
                WHERE id = :id AND brewery_id = :brewery
                """)
                .param("name", plan.name()).param("recipe", plan.recipeId().orElse(null))
                .param("stage", plan.stage().name()).param("status", plan.status().name())
                .param("id", plan.id()).param("brewery", plan.breweryId())
                .update();

        jdbc.sql("DELETE FROM quality_control_point WHERE plan_id = :plan")
                .param("plan", plan.id())
                .update();
        insertPoints(plan);
    }

    private void insertPoints(ControlPlan plan) {
        for (var point : plan.points()) {
            jdbc.sql("""
                    INSERT INTO quality_control_point (id, brewery_id, plan_id, parameter, spec_min, spec_max,
                        spec_target, unit, frequency_kind, every_hours, action, severity, critical)
                    VALUES (:id, :brewery, :plan, :parameter, :min, :max, :target, :unit, :frequency,
                        :hours, :action, :severity, :critical)
                    """)
                    .param("id", point.id()).param("brewery", plan.breweryId()).param("plan", plan.id())
                    .param("parameter", point.parameter())
                    .param("min", point.limits().min()).param("max", point.limits().max())
                    .param("target", point.limits().target()).param("unit", point.limits().unit())
                    .param("frequency", point.frequency().kind().name())
                    .param("hours", point.frequency().everyHours())
                    .param("action", point.action()).param("severity", point.severity().name())
                    .param("critical", point.critical())
                    .update();
        }
    }

    @Override
    public Optional<ControlPlan> findById(UUID breweryId, UUID planId) {
        return load(breweryId, planId, "");
    }

    @Override
    public Optional<ControlPlan> lockById(UUID breweryId, UUID planId) {
        return load(breweryId, planId, " FOR UPDATE");
    }

    private Optional<ControlPlan> load(UUID breweryId, UUID planId, String lock) {
        return jdbc.sql(PLAN_COLUMNS + " WHERE brewery_id = :brewery AND id = :id" + lock)
                .param("brewery", breweryId).param("id", planId)
                .query((rs, n) -> map(rs))
                .optional();
    }

    @Override
    public List<ControlPlan> findAll(UUID breweryId) {
        return jdbc.sql(PLAN_COLUMNS + " WHERE brewery_id = :brewery ORDER BY code, version")
                .param("brewery", breweryId)
                .query((rs, n) -> map(rs))
                .list();
    }

    @Override
    public boolean existsByCodeAndVersion(UUID breweryId, String code, int version) {
        return jdbc.sql("""
                SELECT 1 FROM quality_control_plan
                WHERE brewery_id = :brewery AND code = :code AND version = :version
                """)
                .param("brewery", breweryId).param("code", code).param("version", version)
                .query(Integer.class).optional().isPresent();
    }

    private ControlPlan map(ResultSet rs) throws SQLException {
        var planId = rs.getObject("id", UUID.class);
        return ControlPlan.reconstitute(planId,
                rs.getObject("brewery_id", UUID.class),
                rs.getString("code"),
                rs.getString("name"),
                rs.getObject("recipe_id", UUID.class),
                ProcessStage.valueOf(rs.getString("stage")),
                ControlPlanStatus.valueOf(rs.getString("status")),
                rs.getInt("version"),
                points(planId),
                rs.getLong("lock_version"));
    }

    private List<ControlPoint> points(UUID planId) {
        return jdbc.sql("""
                SELECT id, parameter, spec_min, spec_max, spec_target, unit, frequency_kind, every_hours,
                       action, severity, critical
                FROM quality_control_point WHERE plan_id = :plan ORDER BY parameter
                """)
                .param("plan", planId)
                .query((rs, n) -> ControlPoint.reconstitute(
                        rs.getObject("id", UUID.class),
                        rs.getString("parameter"),
                        new SpecLimits(rs.getBigDecimal("spec_min"), rs.getBigDecimal("spec_max"),
                                rs.getBigDecimal("spec_target"), rs.getString("unit")),
                        new Frequency(FrequencyKind.valueOf(rs.getString("frequency_kind")),
                                rs.getObject("every_hours", Integer.class)),
                        rs.getString("action"),
                        Severity.valueOf(rs.getString("severity")),
                        rs.getBoolean("critical")))
                .list();
    }
}
