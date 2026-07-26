package br.com.brew.brassia.inventory.adapter.outbound.persistence;

import br.com.brew.brassia.inventory.application.port.outbound.StockLotRepository;
import br.com.brew.brassia.inventory.domain.StockInspection;
import br.com.brew.brassia.inventory.domain.StockLot;
import br.com.brew.brassia.inventory.domain.StockLotId;
import br.com.brew.brassia.inventory.domain.StockUnit;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcStockLotRepository implements StockLotRepository {

    private final JdbcClient jdbc;

    JdbcStockLotRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(StockLot l) {
        jdbc.sql("""
                INSERT INTO stock_lot (
                    id, brewery_id, ingredient_id, supplier_id, supplier_lot_code, received_quantity, unit,
                    unit_cost, expiry_date, received_at, inspection, version)
                VALUES (:id, :brewery, :ingredient, :supplier, :lotCode, :qty, :unit, :cost, :expiry, :at,
                        :inspection, :version)
                """)
                .param("id", l.id().value())
                .param("brewery", l.breweryId())
                .param("ingredient", l.ingredientId())
                .param("supplier", l.supplierId())
                .param("lotCode", l.supplierLotCode())
                .param("qty", l.receivedQuantity())
                .param("unit", l.unit().name())
                .param("cost", l.unitCost())
                .param("expiry", l.expiryDate() == null ? null : Date.valueOf(l.expiryDate()))
                .param("at", Timestamp.from(l.receivedAt()))
                .param("inspection", l.inspection().name())
                .param("version", l.version())
                .update();
    }

    private static final String COLUMNS = """
            SELECT id, brewery_id, ingredient_id, supplier_id, supplier_lot_code, received_quantity, unit,
                   unit_cost, expiry_date, received_at, inspection, version
            FROM stock_lot
            """;

    @Override
    public List<StockLot> findAll(UUID breweryId) {
        return jdbc.sql(COLUMNS + " WHERE brewery_id = :brewery ORDER BY received_at DESC")
                .param("brewery", breweryId)
                .query((rs, n) -> map(rs))
                .list();
    }

    @Override
    public java.util.Optional<StockLot> findById(UUID breweryId, UUID lotId) {
        return jdbc.sql(COLUMNS + " WHERE brewery_id = :brewery AND id = :id")
                .param("brewery", breweryId).param("id", lotId)
                .query((rs, n) -> map(rs))
                .optional();
    }

    @Override
    public java.util.Optional<StockLot> lockForUpdate(UUID breweryId, UUID lotId) {
        return jdbc.sql(COLUMNS + " WHERE brewery_id = :brewery AND id = :id FOR UPDATE")
                .param("brewery", breweryId).param("id", lotId)
                .query((rs, n) -> map(rs))
                .optional();
    }

    private static StockLot map(ResultSet rs) throws SQLException {
        var expiry = rs.getDate("expiry_date");
        return StockLot.reconstitute(
                new StockLotId(rs.getObject("id", UUID.class)),
                rs.getObject("brewery_id", UUID.class),
                rs.getObject("ingredient_id", UUID.class),
                rs.getObject("supplier_id", UUID.class),
                rs.getString("supplier_lot_code"),
                rs.getBigDecimal("received_quantity"),
                StockUnit.valueOf(rs.getString("unit")),
                rs.getBigDecimal("unit_cost"),
                expiry == null ? null : expiry.toLocalDate(),
                rs.getTimestamp("received_at").toInstant(),
                StockInspection.valueOf(rs.getString("inspection")),
                rs.getLong("version"));
    }
}
