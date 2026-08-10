package br.com.brew.brassia.production.adapter.outbound.persistence;

import br.com.brew.brassia.production.application.port.outbound.TransferRepository;
import br.com.brew.brassia.production.domain.BatchTransfer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTransferRepository implements TransferRepository {

    private final JdbcClient jdbc;

    JdbcTransferRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(BatchTransfer t) {
        jdbc.sql("""
                INSERT INTO production_transfer (
                    id, brewery_id, batch_id, destination_equipment_id, volume_liters, og_sg, losses_liters,
                    transferred_at, transferred_by)
                VALUES (:id, :brewery, :batch, :dest, :volume, :og, :losses, :at, :by)
                """)
                .param("id", t.id())
                .param("brewery", t.breweryId())
                .param("batch", t.batchId())
                .param("dest", t.destinationEquipmentId())
                .param("volume", t.volumeLiters())
                .param("og", t.ogSg())
                .param("losses", t.lossesLiters())
                .param("at", Timestamp.from(t.transferredAt()))
                .param("by", t.transferredBy())
                .update();
    }

    @Override
    public Optional<BatchTransfer> findByBatch(UUID breweryId, UUID batchId) {
        return jdbc.sql("""
                SELECT id, brewery_id, batch_id, destination_equipment_id, volume_liters, og_sg, losses_liters,
                       transferred_at, transferred_by
                FROM production_transfer
                WHERE brewery_id = :brewery AND batch_id = :batch
                """)
                .param("brewery", breweryId).param("batch", batchId)
                .query((rs, n) -> map(rs))
                .optional();
    }

    @Override
    public Optional<UUID> findFermentingBatchByEquipment(UUID breweryId, UUID equipmentId) {
        return jdbc.sql("""
                SELECT t.batch_id
                FROM production_transfer t
                JOIN production_batch b ON b.id = t.batch_id AND b.brewery_id = t.brewery_id
                WHERE t.brewery_id = :brewery
                  AND t.destination_equipment_id = :equipment
                  AND b.status = 'FERMENTING'
                ORDER BY t.transferred_at DESC
                LIMIT 1
                """)
                .param("brewery", breweryId).param("equipment", equipmentId)
                .query(UUID.class)
                .optional();
    }

    private BatchTransfer map(ResultSet rs) throws SQLException {
        return BatchTransfer.reconstitute(
                rs.getObject("id", UUID.class),
                rs.getObject("brewery_id", UUID.class),
                rs.getObject("batch_id", UUID.class),
                rs.getObject("destination_equipment_id", UUID.class),
                rs.getBigDecimal("volume_liters"),
                rs.getBigDecimal("og_sg"),
                rs.getBigDecimal("losses_liters"),
                rs.getTimestamp("transferred_at").toInstant(),
                rs.getObject("transferred_by", UUID.class));
    }
}
