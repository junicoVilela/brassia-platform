package br.com.brew.brassia.costing.adapter.outbound.persistence;

import br.com.brew.brassia.costing.CostContributor.CostCategory;
import br.com.brew.brassia.costing.CostContributor.CostGap;
import br.com.brew.brassia.costing.CostContributor.CostLine;
import br.com.brew.brassia.costing.application.port.outbound.BatchCostRepository;
import br.com.brew.brassia.costing.domain.BatchCost;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcBatchCostRepository implements BatchCostRepository {

    private final JdbcClient jdbc;

    JdbcBatchCostRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(BatchCost cost) {
        jdbc.sql("""
                INSERT INTO costing_batch_cost (id, brewery_id, batch_id, batch_code, volume_liters,
                        total_cost, note, closed_by, closed_at)
                VALUES (:id, :brewery, :batch, :code, :volume, :total, :note, :by, :at)
                """)
                .param("id", cost.id())
                .param("brewery", cost.breweryId())
                .param("batch", cost.batchId())
                .param("code", cost.batchCode())
                .param("volume", cost.volumeLiters())
                .param("total", cost.total())
                .param("note", cost.note())
                .param("by", cost.closedBy())
                .param("at", Timestamp.from(cost.closedAt()))
                .update();

        for (CostLine line : cost.lines()) {
            jdbc.sql("""
                    INSERT INTO costing_batch_cost_line (id, brewery_id, cost_id, category, description,
                            source, quantity, unit, unit_cost, total)
                    VALUES (:id, :brewery, :cost, :category, :description, :source, :quantity, :unit,
                            :unitCost, :total)
                    """)
                    .param("id", UUID.randomUUID())
                    .param("brewery", cost.breweryId())
                    .param("cost", cost.id())
                    .param("category", line.category().name())
                    .param("description", line.description())
                    .param("source", line.source())
                    .param("quantity", line.quantity())
                    .param("unit", line.unit())
                    .param("unitCost", line.unitCost())
                    .param("total", line.total())
                    .update();
        }

        for (CostGap gap : cost.gaps()) {
            jdbc.sql("""
                    INSERT INTO costing_batch_cost_gap (id, brewery_id, cost_id, category, reason)
                    VALUES (:id, :brewery, :cost, :category, :reason)
                    """)
                    .param("id", UUID.randomUUID())
                    .param("brewery", cost.breweryId())
                    .param("cost", cost.id())
                    .param("category", gap.category().name())
                    .param("reason", gap.reason())
                    .update();
        }
    }

    @Override
    public Optional<BatchCost> findByBatch(UUID breweryId, UUID batchId) {
        return jdbc.sql("""
                SELECT id, brewery_id, batch_id, batch_code, volume_liters, note, closed_by, closed_at
                FROM costing_batch_cost WHERE brewery_id = :brewery AND batch_id = :batch
                """)
                .param("brewery", breweryId).param("batch", batchId)
                .query(this::map).optional();
    }

    @Override
    public List<BatchCost> findAll(UUID breweryId) {
        return jdbc.sql("""
                SELECT id, brewery_id, batch_id, batch_code, volume_liters, note, closed_by, closed_at
                FROM costing_batch_cost WHERE brewery_id = :brewery ORDER BY closed_at DESC
                """)
                .param("brewery", breweryId).query(this::map).list();
    }

    private BatchCost map(ResultSet rs, int rowNum) throws SQLException {
        var id = rs.getObject("id", UUID.class);
        var breweryId = rs.getObject("brewery_id", UUID.class);
        return BatchCost.reconstitute(id, breweryId, rs.getObject("batch_id", UUID.class),
                rs.getString("batch_code"), rs.getBigDecimal("volume_liters"), lines(breweryId, id),
                gaps(breweryId, id), rs.getObject("closed_by", UUID.class),
                rs.getTimestamp("closed_at").toInstant(), rs.getString("note"));
    }

    private List<CostLine> lines(UUID breweryId, UUID costId) {
        return jdbc.sql("""
                SELECT category, description, source, quantity, unit, unit_cost, total
                FROM costing_batch_cost_line WHERE brewery_id = :brewery AND cost_id = :cost
                ORDER BY category, description
                """)
                .param("brewery", breweryId).param("cost", costId)
                .query((rs, rowNum) -> new CostLine(CostCategory.valueOf(rs.getString("category")),
                        rs.getString("description"), rs.getString("source"), rs.getBigDecimal("quantity"),
                        rs.getString("unit"), rs.getBigDecimal("unit_cost"), rs.getBigDecimal("total")))
                .list();
    }

    private List<CostGap> gaps(UUID breweryId, UUID costId) {
        return jdbc.sql("""
                SELECT category, reason FROM costing_batch_cost_gap
                WHERE brewery_id = :brewery AND cost_id = :cost ORDER BY category
                """)
                .param("brewery", breweryId).param("cost", costId)
                .query((rs, rowNum) -> new CostGap(CostCategory.valueOf(rs.getString("category")),
                        rs.getString("reason")))
                .list();
    }
}
