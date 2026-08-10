package br.com.brew.brassia.optimization.adapter.outbound.persistence;

import br.com.brew.brassia.optimization.application.port.outbound.OptimizationRunRepository;
import br.com.brew.brassia.optimization.domain.Candidate;
import br.com.brew.brassia.optimization.domain.Infeasible;
import br.com.brew.brassia.optimization.domain.Objective;
import br.com.brew.brassia.optimization.domain.OptimizationConstraint;
import br.com.brew.brassia.optimization.domain.OptimizationRun;
import br.com.brew.brassia.optimization.domain.SolverMethod;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Corridas de otimização em PostgreSQL (OPT-001).
 *
 * <p>Candidatas e restrições em JSONB porque são estruturas de leitura, não de consulta: ninguém filtra
 * corrida por trade-off. Normalizá-las custaria quatro tabelas para reproduzir, em junções, um documento
 * que só é lido inteiro.
 *
 * <p>{@code updateAnnotations} toca apenas explicação e aplicação. As candidatas não têm caminho de
 * escrita depois da inserção — é o que garante que o score gravado continua sendo o que o solver produziu.
 */
@Repository
class JdbcOptimizationRunRepository implements OptimizationRunRepository {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final JdbcClient jdbc;

    JdbcOptimizationRunRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(OptimizationRun run) {
        jdbc.sql("""
                INSERT INTO optimization_run (id, brewery_id, recipe_id, recipe_version, objective,
                        constraints, method, catalog_version, seed, candidates, infeasible,
                        requested_by, requested_at)
                VALUES (:id, :brewery, :recipe, :recipeVersion, :objective,
                        CAST(:constraints AS jsonb), :method, :catalogVersion, :seed,
                        CAST(:candidates AS jsonb), CAST(:infeasible AS jsonb), :by, :at)
                """)
                .param("id", run.id())
                .param("brewery", run.breweryId())
                .param("recipe", run.recipeId())
                .param("recipeVersion", run.recipeVersion())
                .param("objective", run.objective().name())
                .param("constraints", write(run.constraints()))
                .param("method", run.method().name())
                .param("catalogVersion", run.catalogVersion())
                .param("seed", run.seed().orElse(null))
                .param("candidates", write(run.candidates()))
                .param("infeasible", run.infeasible().map(JdbcOptimizationRunRepository::write)
                        .orElse(null))
                .param("by", run.requestedBy())
                .param("at", Timestamp.from(run.requestedAt()))
                .update();
    }

    @Override
    public void updateAnnotations(OptimizationRun run) {
        jdbc.sql("""
                UPDATE optimization_run
                SET explanation = :explanation, applied_recipe_version_id = :applied
                WHERE id = :id AND brewery_id = :brewery
                """)
                .param("explanation", run.explanation().orElse(null))
                .param("applied", run.appliedRecipeVersionId().orElse(null))
                .param("id", run.id())
                .param("brewery", run.breweryId())
                .update();
    }

    @Override
    public Optional<OptimizationRun> find(UUID breweryId, UUID runId) {
        return jdbc.sql(SELECT + " WHERE id = :id AND brewery_id = :brewery")
                .param("id", runId).param("brewery", breweryId)
                .query(this::map).optional();
    }

    @Override
    public Optional<OptimizationRun> findForUpdate(UUID breweryId, UUID runId) {
        return jdbc.sql(SELECT + " WHERE id = :id AND brewery_id = :brewery FOR UPDATE")
                .param("id", runId).param("brewery", breweryId)
                .query(this::map).optional();
    }

    @Override
    public List<OptimizationRun> list(UUID breweryId, UUID recipeId) {
        var sql = recipeId == null
                ? SELECT + " WHERE brewery_id = :brewery ORDER BY requested_at DESC"
                : SELECT + " WHERE brewery_id = :brewery AND recipe_id = :recipe "
                        + "ORDER BY requested_at DESC";
        var spec = jdbc.sql(sql).param("brewery", breweryId);
        if (recipeId != null) {
            spec = spec.param("recipe", recipeId);
        }
        return spec.query(this::map).list();
    }

    private static final String SELECT = """
            SELECT id, brewery_id, recipe_id, recipe_version, objective, constraints, method,
                   catalog_version, seed, candidates, infeasible, explanation,
                   applied_recipe_version_id, requested_by, requested_at
            FROM optimization_run
            """;

    private OptimizationRun map(ResultSet rs, int rowNum) throws SQLException {
        var seed = rs.getObject("seed", Long.class);
        return OptimizationRun.reconstitute(
                rs.getObject("id", UUID.class),
                rs.getObject("brewery_id", UUID.class),
                rs.getObject("recipe_id", UUID.class),
                rs.getInt("recipe_version"),
                Objective.valueOf(rs.getString("objective")),
                read(rs.getString("constraints"), new TypeReference<List<OptimizationConstraint>>() {}),
                SolverMethod.valueOf(rs.getString("method")),
                rs.getString("catalog_version"),
                seed,
                read(rs.getString("candidates"), new TypeReference<List<Candidate>>() {}),
                readOne(rs.getString("infeasible")),
                rs.getString("explanation"),
                rs.getObject("applied_recipe_version_id", UUID.class),
                rs.getObject("requested_by", UUID.class),
                rs.getTimestamp("requested_at").toInstant());
    }

    private static String write(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("não foi possível serializar o resultado da otimização", e);
        }
    }

    private static <T> T read(String json, TypeReference<T> type) {
        try {
            return JSON.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("não foi possível ler o resultado da otimização", e);
        }
    }

    private static Infeasible readOne(String json) {
        if (json == null) {
            return null;
        }
        try {
            return JSON.readValue(json, Infeasible.class);
        } catch (Exception e) {
            throw new IllegalStateException("não foi possível ler a inviabilidade", e);
        }
    }
}
