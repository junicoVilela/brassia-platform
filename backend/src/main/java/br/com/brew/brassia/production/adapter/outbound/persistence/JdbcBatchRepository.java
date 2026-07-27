package br.com.brew.brassia.production.adapter.outbound.persistence;

import br.com.brew.brassia.production.application.port.outbound.BatchRepository;
import br.com.brew.brassia.production.domain.Batch;
import br.com.brew.brassia.production.domain.BatchId;
import br.com.brew.brassia.production.domain.BatchStatus;
import br.com.brew.brassia.production.domain.BatchStep;
import br.com.brew.brassia.production.domain.BatchStepType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcBatchRepository implements BatchRepository {

    private static final String COLUMNS = """
            SELECT id, brewery_id, order_id, code, recipe_id, recipe_version, recipe_name, volume_liters,
                   status, started_at, started_by
            FROM production_batch
            """;

    private final JdbcClient jdbc;

    JdbcBatchRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Batch b) {
        jdbc.sql("""
                INSERT INTO production_batch (
                    id, brewery_id, order_id, code, recipe_id, recipe_version, recipe_name, volume_liters,
                    status, started_at, started_by)
                VALUES (:id, :brewery, :order, :code, :recipe, :recipeVersion, :recipeName, :volume,
                        :status, :at, :by)
                """)
                .param("id", b.id().value())
                .param("brewery", b.breweryId())
                .param("order", b.orderId())
                .param("code", b.code())
                .param("recipe", b.recipeId())
                .param("recipeVersion", b.recipeVersion())
                .param("recipeName", b.recipeName())
                .param("volume", b.volumeLiters())
                .param("status", b.status().name())
                .param("at", Timestamp.from(b.startedAt()))
                .param("by", b.startedBy())
                .update();

        for (var step : b.steps()) {
            jdbc.sql("""
                    INSERT INTO production_batch_step (id, batch_id, brewery_id, step_order, type, label)
                    VALUES (:id, :batch, :brewery, :stepOrder, :type, :label)
                    """)
                    .param("id", step.id())
                    .param("batch", b.id().value())
                    .param("brewery", b.breweryId())
                    .param("stepOrder", step.sequence())
                    .param("type", step.type().name())
                    .param("label", step.label())
                    .update();
        }
    }

    @Override
    public boolean existsByOrder(UUID breweryId, UUID orderId) {
        return jdbc.sql("SELECT 1 FROM production_batch WHERE brewery_id = :brewery AND order_id = :order")
                .param("brewery", breweryId).param("order", orderId)
                .query(Integer.class).optional().isPresent();
    }

    @Override
    public List<Batch> findAll(UUID breweryId) {
        return jdbc.sql(COLUMNS + " WHERE brewery_id = :brewery ORDER BY started_at DESC")
                .param("brewery", breweryId)
                .query((rs, n) -> map(rs))
                .list();
    }

    @Override
    public Optional<Batch> findById(UUID breweryId, UUID batchId) {
        return jdbc.sql(COLUMNS + " WHERE brewery_id = :brewery AND id = :id")
                .param("brewery", breweryId).param("id", batchId)
                .query((rs, n) -> map(rs))
                .optional();
    }

    private Batch map(ResultSet rs) throws SQLException {
        var batchId = rs.getObject("id", UUID.class);
        var breweryId = rs.getObject("brewery_id", UUID.class);
        return Batch.reconstitute(
                new BatchId(batchId),
                breweryId,
                rs.getObject("order_id", UUID.class),
                rs.getString("code"),
                rs.getObject("recipe_id", UUID.class),
                rs.getInt("recipe_version"),
                rs.getString("recipe_name"),
                rs.getBigDecimal("volume_liters"),
                BatchStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("started_at").toInstant(),
                rs.getObject("started_by", UUID.class),
                steps(breweryId, batchId));
    }

    private List<BatchStep> steps(UUID breweryId, UUID batchId) {
        return jdbc.sql("""
                SELECT id, step_order, type, label FROM production_batch_step
                WHERE brewery_id = :brewery AND batch_id = :batch ORDER BY step_order
                """)
                .param("brewery", breweryId).param("batch", batchId)
                .query((rs, n) -> new BatchStep(
                        rs.getObject("id", UUID.class),
                        rs.getInt("step_order"),
                        BatchStepType.valueOf(rs.getString("type")),
                        rs.getString("label")))
                .list();
    }
}
