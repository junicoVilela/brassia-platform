package br.com.brew.brassia.experiment.adapter.outbound.persistence;

import br.com.brew.brassia.experiment.application.port.outbound.ExperimentRepository;
import br.com.brew.brassia.experiment.domain.Conclusion;
import br.com.brew.brassia.experiment.domain.ExperimentFactor;
import br.com.brew.brassia.experiment.domain.ExperimentPlan;
import br.com.brew.brassia.experiment.domain.ExperimentStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Experimentos em PostgreSQL (EXP-001).
 *
 * <p><strong>{@code updateProgress} escreve estado e conclusão, e mais nada.</strong> Não existe caminho
 * que altere hipótese, fatores ou grandezas — nem por engano, porque o SQL não menciona essas colunas. Um
 * experimento cuja hipótese pode ser reescrita depois do resultado sempre confirma a hipótese.
 *
 * <p>As limitações não são gravadas: derivam do plano na leitura. Gravá-las abriria a possibilidade de uma
 * conclusão com limitações editadas.
 */
@Repository
class JdbcExperimentRepository implements ExperimentRepository {

    private final JdbcClient jdbc;

    JdbcExperimentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(ExperimentPlan plan) {
        jdbc.sql("""
                INSERT INTO experiment_plan (id, brewery_id, recipe_id, hypothesis, control_batch_id,
                        variant_batch_id, sensory_planned, sensory_blind, status, planned_by, planned_at)
                VALUES (:id, :brewery, :recipe, :hypothesis, :control, :variant, :sensory, :blind,
                        :status, :by, :at)
                """)
                .param("id", plan.id())
                .param("brewery", plan.breweryId())
                .param("recipe", plan.recipeId())
                .param("hypothesis", plan.hypothesis())
                .param("control", plan.controlBatchId())
                .param("variant", plan.variantBatchId())
                .param("sensory", plan.sensoryPlanned())
                .param("blind", plan.sensoryBlind())
                .param("status", plan.status().name())
                .param("by", plan.plannedBy())
                .param("at", Timestamp.from(plan.plannedAt()))
                .update();

        for (var factor : plan.factors()) {
            jdbc.sql("""
                    INSERT INTO experiment_factor (experiment_id, name, control_value, variant_value)
                    VALUES (:experiment, :name, :control, :variant)
                    """)
                    .param("experiment", plan.id())
                    .param("name", factor.name())
                    .param("control", factor.controlValue())
                    .param("variant", factor.variantValue())
                    .update();
        }

        for (var kind : plan.plannedMeasurements()) {
            jdbc.sql("INSERT INTO experiment_measurement_plan (experiment_id, kind) "
                    + "VALUES (:experiment, :kind)")
                    .param("experiment", plan.id()).param("kind", kind)
                    .update();
        }
    }

    @Override
    public void updateProgress(ExperimentPlan plan) {
        var conclusion = plan.conclusion().orElse(null);
        jdbc.sql("""
                UPDATE experiment_plan
                SET status = :status,
                    conclusion_supported = :supported,
                    conclusion_observation = :observation,
                    concluded_by = :by,
                    concluded_at = :at
                WHERE id = :id AND brewery_id = :brewery
                """)
                .param("status", plan.status().name())
                .param("supported", conclusion == null ? null : conclusion.supported())
                .param("observation", conclusion == null ? null : conclusion.observation())
                .param("by", conclusion == null ? null : conclusion.concludedBy())
                .param("at", conclusion == null ? null : Timestamp.from(conclusion.concludedAt()))
                .param("id", plan.id())
                .param("brewery", plan.breweryId())
                .update();
    }

    @Override
    public Optional<ExperimentPlan> find(UUID breweryId, UUID experimentId) {
        return jdbc.sql(SELECT + " WHERE id = :id AND brewery_id = :brewery")
                .param("id", experimentId).param("brewery", breweryId)
                .query(this::map).optional();
    }

    @Override
    public Optional<ExperimentPlan> findForUpdate(UUID breweryId, UUID experimentId) {
        return jdbc.sql(SELECT + " WHERE id = :id AND brewery_id = :brewery FOR UPDATE")
                .param("id", experimentId).param("brewery", breweryId)
                .query(this::map).optional();
    }

    @Override
    public List<ExperimentPlan> listOf(UUID breweryId, UUID recipeId) {
        return jdbc.sql(SELECT + " WHERE brewery_id = :brewery AND recipe_id = :recipe "
                + "ORDER BY planned_at DESC")
                .param("brewery", breweryId).param("recipe", recipeId)
                .query(this::map).list();
    }

    @Override
    public List<ExperimentPlan> listAll(UUID breweryId) {
        return jdbc.sql(SELECT + " WHERE brewery_id = :brewery ORDER BY planned_at DESC")
                .param("brewery", breweryId)
                .query(this::map).list();
    }

    private static final String SELECT = """
            SELECT id, brewery_id, recipe_id, hypothesis, control_batch_id, variant_batch_id,
                   sensory_planned, sensory_blind, status, planned_by, planned_at,
                   conclusion_supported, conclusion_observation, concluded_by, concluded_at
            FROM experiment_plan
            """;

    private ExperimentPlan map(ResultSet rs, int rowNum) throws SQLException {
        var id = rs.getObject("id", UUID.class);
        var concludedAt = rs.getTimestamp("concluded_at");
        // Monta-se primeiro SEM a conclusão porque as limitações dela derivam do plano, e o plano precisa
        // existir para serem calculadas. Só então a conclusão é anexada, com a lista derivada — a mesma
        // que ela carregou na origem, porque o plano é imutável.
        var plan = ExperimentPlan.reconstitute(id,
                rs.getObject("brewery_id", UUID.class),
                rs.getObject("recipe_id", UUID.class),
                rs.getString("hypothesis"),
                rs.getObject("control_batch_id", UUID.class),
                rs.getObject("variant_batch_id", UUID.class),
                factorsOf(id),
                measurementsOf(id),
                rs.getBoolean("sensory_planned"),
                rs.getBoolean("sensory_blind"),
                ExperimentStatus.valueOf(rs.getString("status")),
                null,
                rs.getObject("planned_by", UUID.class),
                rs.getTimestamp("planned_at").toInstant());

        if (concludedAt != null) {
            return ExperimentPlan.reconstitute(plan.id(), plan.breweryId(), plan.recipeId(),
                    plan.hypothesis(), plan.controlBatchId(), plan.variantBatchId(), plan.factors(),
                    plan.plannedMeasurements(), plan.sensoryPlanned(), plan.sensoryBlind(),
                    plan.status(),
                    new Conclusion(rs.getBoolean("conclusion_supported"),
                            rs.getString("conclusion_observation"), plan.limitations(),
                            rs.getObject("concluded_by", UUID.class), concludedAt.toInstant()),
                    plan.plannedBy(), plan.plannedAt());
        }
        return plan;
    }

    private List<ExperimentFactor> factorsOf(UUID experimentId) {
        return jdbc.sql("""
                SELECT name, control_value, variant_value FROM experiment_factor
                WHERE experiment_id = :experiment ORDER BY name
                """)
                .param("experiment", experimentId)
                .query((rs, n) -> new ExperimentFactor(rs.getString("name"),
                        rs.getString("control_value"), rs.getString("variant_value")))
                .list();
    }

    private Set<String> measurementsOf(UUID experimentId) {
        return new LinkedHashSet<>(jdbc.sql(
                "SELECT kind FROM experiment_measurement_plan WHERE experiment_id = :experiment "
                        + "ORDER BY kind")
                .param("experiment", experimentId)
                .query(String.class).list());
    }
}
