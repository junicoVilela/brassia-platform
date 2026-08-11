package br.com.brew.brassia.production.adapter.outbound.persistence;

import br.com.brew.brassia.production.application.port.outbound.BatchRepository;
import br.com.brew.brassia.production.domain.Batch;
import br.com.brew.brassia.production.domain.BatchId;
import br.com.brew.brassia.production.domain.BatchOrigin;
import br.com.brew.brassia.production.domain.BatchStatus;
import br.com.brew.brassia.production.domain.BatchStep;
import br.com.brew.brassia.production.domain.BatchStepStatus;
import br.com.brew.brassia.production.domain.BatchStepType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcBatchRepository implements BatchRepository {

    private static final String COLUMNS = """
            SELECT id, brewery_id, order_id, origin, code, recipe_id, recipe_version, recipe_name,
                   volume_liters, status, started_at, started_by
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
                    id, brewery_id, order_id, origin, code, recipe_id, recipe_version, recipe_name,
                    volume_liters, status, started_at, started_by)
                VALUES (:id, :brewery, :order, :origin, :code, :recipe, :recipeVersion, :recipeName, :volume,
                        :status, :at, :by)
                """)
                .param("id", b.id().value())
                .param("brewery", b.breweryId())
                .param("order", b.orderId())
                .param("origin", b.origin().name())
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
                    INSERT INTO production_batch_step (
                        id, batch_id, brewery_id, step_order, type, label, step_status, started_at, completed_at)
                    VALUES (:id, :batch, :brewery, :stepOrder, :type, :label, :status, :startedAt, :completedAt)
                    """)
                    .param("id", step.id())
                    .param("batch", b.id().value())
                    .param("brewery", b.breweryId())
                    .param("stepOrder", step.sequence())
                    .param("type", step.type().name())
                    .param("label", step.label())
                    .param("status", step.status().name())
                    .param("startedAt", step.startedAt() == null ? null : Timestamp.from(step.startedAt()))
                    .param("completedAt", step.completedAt() == null ? null : Timestamp.from(step.completedAt()))
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
    public List<Batch> findPage(UUID breweryId, int offset, int limit) {
        var rows = jdbc.sql(COLUMNS + """
                 WHERE brewery_id = :brewery
                 ORDER BY started_at DESC, id
                 LIMIT :limit OFFSET :offset
                """)
                .param("brewery", breweryId).param("limit", limit).param("offset", offset)
                .query((rs, n) -> new Row(rs))
                .list();

        // Os passos vêm em UMA consulta para a página inteira, não uma por lote.
        //
        // O `map` original chamava `steps()` por linha: listar 3.000 lotes disparava 3.001 consultas, e
        // era esse N+1 — não o tamanho do JSON — que fazia a listagem crescer linearmente (REL-002).
        // Paginar sozinho esconderia o problema numa página de 20; ele voltaria em qualquer lugar que
        // lesse muitos lotes.
        var stepsByBatch = stepsOf(breweryId, rows.stream().map(Row::id).toList());
        return rows.stream()
                .map(row -> row.toBatch(stepsByBatch.getOrDefault(row.id(), List.of())))
                .toList();
    }

    @Override
    public long countByBrewery(UUID breweryId) {
        return jdbc.sql("SELECT count(*) FROM production_batch WHERE brewery_id = :brewery")
                .param("brewery", breweryId)
                .query(Long.class).single();
    }

    /**
     * Passos de vários lotes de uma vez.
     *
     * <p>Devolve mapa vazio para lista vazia: montar {@code IN ()} com nenhum id é erro de sintaxe no
     * PostgreSQL, e a página vazia é caso normal — a última página de qualquer listagem.
     */
    private Map<UUID, List<BatchStep>> stepsOf(UUID breweryId, List<UUID> batchIds) {
        if (batchIds.isEmpty()) {
            return Map.of();
        }
        var byBatch = new HashMap<UUID, List<BatchStep>>();
        jdbc.sql("""
                SELECT batch_id, id, step_order, type, label, step_status, started_at, completed_at
                FROM production_batch_step
                WHERE brewery_id = :brewery AND batch_id IN (:batches)
                ORDER BY batch_id, step_order
                """)
                .param("brewery", breweryId).param("batches", batchIds)
                .query((rs, n) -> {
                    var batchId = rs.getObject("batch_id", UUID.class);
                    byBatch.computeIfAbsent(batchId, k -> new ArrayList<>()).add(readStep(rs));
                    return batchId;
                })
                .list();
        return byBatch;
    }

    @Override
    public Optional<Batch> findById(UUID breweryId, UUID batchId) {
        return jdbc.sql(COLUMNS + " WHERE brewery_id = :brewery AND id = :id")
                .param("brewery", breweryId).param("id", batchId)
                .query((rs, n) -> map(rs))
                .optional();
    }

    @Override
    public boolean markCompleted(UUID breweryId, UUID batchId, java.time.Instant at) {
        // Os dois estados vivos encerram, e não só FERMENTING: um lote sem cerveja acabou, esteja ele em
        // brassa ou em fermentação. Restringir a FERMENTING deixaria um lote drenado em brassa aberto para
        // sempre, aparecendo como disponível para uma cerveja que não está mais lá.
        //
        // O estado antigo está no WHERE: duas execuções simultâneas de blend esvaziando o mesmo lote não
        // encerram duas vezes, e a segunda descobre isso pelo banco em vez de por uma leitura anterior.
        return jdbc.sql("""
                UPDATE production_batch SET status = 'COMPLETED'
                WHERE brewery_id = :brewery AND id = :batch AND status IN ('IN_PROGRESS', 'FERMENTING')
                """)
                .param("brewery", breweryId).param("batch", batchId)
                .update() == 1;
    }

    private Batch map(ResultSet rs) throws SQLException {
        var batchId = rs.getObject("id", UUID.class);
        var breweryId = rs.getObject("brewery_id", UUID.class);
        return Batch.reconstitute(
                new BatchId(batchId),
                breweryId,
                rs.getObject("order_id", UUID.class),
                BatchOrigin.valueOf(rs.getString("origin")),
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

    /** Passos de UM lote. Continua servindo o detalhe, onde uma consulta por lote é o custo certo. */
    private List<BatchStep> steps(UUID breweryId, UUID batchId) {
        return jdbc.sql("""
                SELECT id, step_order, type, label, step_status, started_at, completed_at
                FROM production_batch_step
                WHERE brewery_id = :brewery AND batch_id = :batch ORDER BY step_order
                """)
                .param("brewery", breweryId).param("batch", batchId)
                .query((rs, n) -> readStep(rs))
                .list();
    }

    /** Leitura de um passo, compartilhada pelo detalhe e pela carga em lote da listagem. */
    private static BatchStep readStep(ResultSet rs) throws SQLException {
        var startedAt = rs.getTimestamp("started_at");
        var completedAt = rs.getTimestamp("completed_at");
        return new BatchStep(
                rs.getObject("id", UUID.class),
                rs.getInt("step_order"),
                BatchStepType.valueOf(rs.getString("type")),
                rs.getString("label"),
                BatchStepStatus.valueOf(rs.getString("step_status")),
                startedAt == null ? null : startedAt.toInstant(),
                completedAt == null ? null : completedAt.toInstant());
    }

    /**
     * Uma linha de lote sem os passos, para a listagem carregá-los em bloco depois.
     *
     * <p>Existe porque o {@code map} original resolvia os passos dentro do próprio mapeamento — o que
     * torna o N+1 invisível em quem lê o código: a consulta extra não aparece na chamada, aparece no
     * mapeador.
     */
    private record Row(UUID id, UUID breweryId, UUID orderId, BatchOrigin origin, String code, UUID recipeId,
            int recipeVersion, String recipeName, java.math.BigDecimal volumeLiters, BatchStatus status,
            java.time.Instant startedAt, UUID startedBy) {

        Row(ResultSet rs) throws SQLException {
            this(rs.getObject("id", UUID.class),
                    rs.getObject("brewery_id", UUID.class),
                    rs.getObject("order_id", UUID.class),
                    BatchOrigin.valueOf(rs.getString("origin")),
                    rs.getString("code"),
                    rs.getObject("recipe_id", UUID.class),
                    rs.getInt("recipe_version"),
                    rs.getString("recipe_name"),
                    rs.getBigDecimal("volume_liters"),
                    BatchStatus.valueOf(rs.getString("status")),
                    rs.getTimestamp("started_at").toInstant(),
                    rs.getObject("started_by", UUID.class));
        }

        Batch toBatch(List<BatchStep> steps) {
            return Batch.reconstitute(new BatchId(id), breweryId, orderId, origin, code, recipeId,
                    recipeVersion, recipeName, volumeLiters, status, startedAt, startedBy, steps);
        }
    }

    @Override
    public boolean markFermenting(UUID breweryId, UUID batchId, java.time.Instant at) {
        int updated = jdbc.sql("""
                UPDATE production_batch SET status = 'FERMENTING'
                WHERE brewery_id = :brewery AND id = :batch AND status = 'IN_PROGRESS'
                """)
                .param("brewery", breweryId).param("batch", batchId)
                .update();
        return updated > 0;
    }

    @Override
    public boolean completeStep(UUID breweryId, UUID batchId, UUID stepId, UUID nextStepId, java.time.Instant at) {
        // Guarda de sequência/concorrência: só conclui a etapa que está ATIVA.
        int done = jdbc.sql("""
                UPDATE production_batch_step
                SET step_status = 'DONE', completed_at = :at
                WHERE brewery_id = :brewery AND batch_id = :batch AND id = :step AND step_status = 'ACTIVE'
                """)
                .param("brewery", breweryId).param("batch", batchId).param("step", stepId)
                .param("at", Timestamp.from(at))
                .update();
        if (done == 0) {
            return false;
        }
        if (nextStepId != null) {
            jdbc.sql("""
                    UPDATE production_batch_step
                    SET step_status = 'ACTIVE', started_at = :at
                    WHERE brewery_id = :brewery AND batch_id = :batch AND id = :step AND step_status = 'PENDING'
                    """)
                    .param("brewery", breweryId).param("batch", batchId).param("step", nextStepId)
                    .param("at", Timestamp.from(at))
                    .update();
        }
        return true;
    }
}
