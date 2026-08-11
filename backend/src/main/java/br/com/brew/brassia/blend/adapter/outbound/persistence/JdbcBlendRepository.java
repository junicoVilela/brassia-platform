package br.com.brew.brassia.blend.adapter.outbound.persistence;

import br.com.brew.brassia.blend.application.port.outbound.BlendRepository;
import br.com.brew.brassia.blend.domain.BlendKind;
import br.com.brew.brassia.blend.domain.BlendOperation;
import br.com.brew.brassia.blend.domain.BlendStatus;
import br.com.brew.brassia.blend.domain.PlannedOutput;
import br.com.brew.brassia.blend.domain.VolumeMovement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Operações de blend em PostgreSQL (BLD-001).
 *
 * <p>{@code updateProgress} não menciona movimentos, motivo nem tipo — o que já foi simulado e aprovado
 * descreve uma decisão tomada sobre aqueles volumes. Editá-los depois faria a aprovação parecer dada
 * sobre números que ninguém aprovou.
 */
@Repository
class JdbcBlendRepository implements BlendRepository {

    private final JdbcClient jdbc;

    JdbcBlendRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(BlendOperation operation) {
        jdbc.sql("""
                INSERT INTO blend_operation (id, brewery_id, kind, declared_loss_liters, reason, status,
                        simulated_by, simulated_at)
                VALUES (:id, :brewery, :kind, :loss, :reason, :status, :by, :at)
                """)
                .param("id", operation.id())
                .param("brewery", operation.breweryId())
                .param("kind", operation.kind().name())
                .param("loss", operation.declaredLossLiters())
                .param("reason", operation.reason())
                .param("status", operation.status().name())
                .param("by", operation.simulatedBy())
                .param("at", Timestamp.from(operation.simulatedAt()))
                .update();

        insertSide(operation.id(), "INPUT", operation.inputs());
        insertSide(operation.id(), "OUTPUT", operation.outputs());

        for (var planned : operation.plannedOutputs()) {
            jdbc.sql("""
                    INSERT INTO blend_planned_output (operation_id, seq, recipe_id, equipment_id, liters)
                    VALUES (:operation, :seq, :recipe, :equipment, :liters)
                    """)
                    .param("operation", operation.id()).param("seq", planned.seq())
                    .param("recipe", planned.recipeId()).param("equipment", planned.equipmentId())
                    .param("liters", planned.liters())
                    .update();
        }
    }

    /**
     * Grava o lote criado para a saída planejada.
     *
     * <p>Separado do {@code updateProgress} porque acontece depois dele: o lote só existe quando a
     * operação já está executada, e é a execução que autoriza criá-lo.
     */
    @Override
    public void linkResultBatch(UUID operationId, int seq, UUID batchId) {
        jdbc.sql("""
                UPDATE blend_planned_output SET created_batch_id = :batch
                WHERE operation_id = :operation AND seq = :seq AND created_batch_id IS NULL
                """)
                .param("batch", batchId).param("operation", operationId).param("seq", seq)
                .update();
    }

    private void insertSide(UUID operationId, String side, List<VolumeMovement> movements) {
        for (var movement : movements) {
            jdbc.sql("""
                    INSERT INTO blend_movement (operation_id, side, batch_id, liters)
                    VALUES (:operation, :side, :batch, :liters)
                    """)
                    .param("operation", operationId).param("side", side)
                    .param("batch", movement.batchId()).param("liters", movement.liters())
                    .update();
        }
    }

    @Override
    public void updateProgress(BlendOperation operation) {
        jdbc.sql("""
                UPDATE blend_operation
                SET status = :status, approved_by = :approvedBy, approved_at = :approvedAt,
                    executed_by = :executedBy, executed_at = :executedAt
                WHERE id = :id AND brewery_id = :brewery
                """)
                .param("status", operation.status().name())
                .param("approvedBy", operation.approvedBy().orElse(null))
                .param("approvedAt", operation.approvedAt().map(Timestamp::from).orElse(null))
                .param("executedBy", operation.executedBy().orElse(null))
                .param("executedAt", operation.executedAt().map(Timestamp::from).orElse(null))
                .param("id", operation.id())
                .param("brewery", operation.breweryId())
                .update();
    }

    @Override
    public Optional<BlendOperation> find(UUID breweryId, UUID operationId) {
        return jdbc.sql(SELECT + " WHERE id = :id AND brewery_id = :brewery")
                .param("id", operationId).param("brewery", breweryId)
                .query(this::map).optional();
    }

    @Override
    public Optional<BlendOperation> findForUpdate(UUID breweryId, UUID operationId) {
        return jdbc.sql(SELECT + " WHERE id = :id AND brewery_id = :brewery FOR UPDATE")
                .param("id", operationId).param("brewery", breweryId)
                .query(this::map).optional();
    }

    @Override
    public List<BlendOperation> list(UUID breweryId) {
        return jdbc.sql(SELECT + " WHERE brewery_id = :brewery ORDER BY simulated_at DESC")
                .param("brewery", breweryId)
                .query(this::map).list();
    }

    /**
     * Operações executadas que tocam o lote, de qualquer lado.
     *
     * <p>Só executadas: uma simulação não moveu cerveja, e uma aresta de genealogia sobre ela faria o
     * recall alcançar lotes que nunca se tocaram.
     */
    @Override
    public List<BlendOperation> executedTouching(UUID breweryId, UUID batchId) {
        return jdbc.sql(SELECT + """
                 WHERE brewery_id = :brewery AND status = 'EXECUTED'
                   AND (EXISTS (SELECT 1 FROM blend_movement m
                                WHERE m.operation_id = blend_operation.id AND m.batch_id = :batch)
                        -- O lote criado pela operação também é tocado por ela: sem esta metade, a
                        -- genealogia pararia justamente no lote que a união produziu.
                        OR EXISTS (SELECT 1 FROM blend_planned_output p
                                   WHERE p.operation_id = blend_operation.id
                                     AND p.created_batch_id = :batch))
                 ORDER BY executed_at
                """)
                .param("brewery", breweryId).param("batch", batchId)
                .query(this::map).list();
    }

    private static final String SELECT = """
            SELECT id, brewery_id, kind, declared_loss_liters, reason, status, simulated_by,
                   simulated_at, approved_by, approved_at, executed_by, executed_at
            FROM blend_operation
            """;

    private BlendOperation map(ResultSet rs, int rowNum) throws SQLException {
        var id = rs.getObject("id", UUID.class);
        return BlendOperation.reconstitute(id,
                rs.getObject("brewery_id", UUID.class),
                BlendKind.valueOf(rs.getString("kind")),
                movementsOf(id, "INPUT"),
                movementsOf(id, "OUTPUT"),
                plannedOutputsOf(id),
                resultBatchesOf(id),
                rs.getBigDecimal("declared_loss_liters"),
                rs.getString("reason"),
                BlendStatus.valueOf(rs.getString("status")),
                rs.getObject("simulated_by", UUID.class),
                rs.getTimestamp("simulated_at").toInstant(),
                rs.getObject("approved_by", UUID.class),
                instantOf(rs.getTimestamp("approved_at")),
                rs.getObject("executed_by", UUID.class),
                instantOf(rs.getTimestamp("executed_at")));
    }

    private static Instant instantOf(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private List<PlannedOutput> plannedOutputsOf(UUID operationId) {
        return jdbc.sql("""
                SELECT seq, recipe_id, equipment_id, liters FROM blend_planned_output
                WHERE operation_id = :operation ORDER BY seq
                """)
                .param("operation", operationId)
                .query((rs, n) -> new PlannedOutput(rs.getInt("seq"),
                        rs.getObject("recipe_id", UUID.class), rs.getObject("equipment_id", UUID.class),
                        rs.getBigDecimal("liters")))
                .list();
    }

    private Map<Integer, UUID> resultBatchesOf(UUID operationId) {
        var links = new LinkedHashMap<Integer, UUID>();
        jdbc.sql("""
                SELECT seq, created_batch_id FROM blend_planned_output
                WHERE operation_id = :operation AND created_batch_id IS NOT NULL ORDER BY seq
                """)
                .param("operation", operationId)
                .query((rs, n) -> Map.entry(rs.getInt("seq"), rs.getObject("created_batch_id", UUID.class)))
                .list()
                .forEach(entry -> links.put(entry.getKey(), entry.getValue()));
        return links;
    }

    private List<VolumeMovement> movementsOf(UUID operationId, String side) {
        return jdbc.sql("""
                SELECT batch_id, liters FROM blend_movement
                WHERE operation_id = :operation AND side = :side ORDER BY batch_id
                """)
                .param("operation", operationId).param("side", side)
                .query((rs, n) -> new VolumeMovement(rs.getObject("batch_id", UUID.class),
                        rs.getBigDecimal("liters")))
                .list();
    }
}
