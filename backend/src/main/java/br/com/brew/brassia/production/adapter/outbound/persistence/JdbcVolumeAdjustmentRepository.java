package br.com.brew.brassia.production.adapter.outbound.persistence;

import br.com.brew.brassia.production.application.port.outbound.VolumeAdjustmentRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcVolumeAdjustmentRepository implements VolumeAdjustmentRepository {

    private final JdbcClient jdbc;

    JdbcVolumeAdjustmentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insert(UUID breweryId, UUID batchId, BigDecimal deltaLiters, String source,
            UUID sourceRef, UUID actorId, Instant occurredAt) {
        try {
            jdbc.sql("""
                    INSERT INTO production_volume_adjustment (id, brewery_id, batch_id, delta_liters,
                            source, source_ref, actor_id, occurred_at)
                    VALUES (:id, :brewery, :batch, :delta, :source, :ref, :actor, :at)
                    """)
                    .param("id", UUID.randomUUID())
                    .param("brewery", breweryId)
                    .param("batch", batchId)
                    .param("delta", deltaLiters)
                    .param("source", source)
                    .param("ref", sourceRef)
                    .param("actor", actorId)
                    .param("at", Timestamp.from(occurredAt))
                    .update();
            return true;
        } catch (DuplicateKeyException repeated) {
            // A restrição única traduzida: a mesma operação já ajustou este lote. Repetir a execução não
            // pode ser cumulativo — seria cerveja saindo duas vezes do mesmo tanque.
            return false;
        }
    }

    @Override
    public BigDecimal totalFor(UUID breweryId, UUID batchId) {
        return jdbc.sql("""
                SELECT COALESCE(SUM(delta_liters), 0) FROM production_volume_adjustment
                WHERE brewery_id = :brewery AND batch_id = :batch
                """)
                .param("brewery", breweryId).param("batch", batchId)
                .query(BigDecimal.class).single();
    }
}
