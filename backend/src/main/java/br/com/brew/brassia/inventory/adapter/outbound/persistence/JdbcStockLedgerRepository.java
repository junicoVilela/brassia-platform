package br.com.brew.brassia.inventory.adapter.outbound.persistence;

import br.com.brew.brassia.inventory.application.port.outbound.StockLedgerRepository;
import br.com.brew.brassia.inventory.domain.StockBalance;
import br.com.brew.brassia.inventory.domain.StockMovement;
import br.com.brew.brassia.inventory.domain.StockMovementType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcStockLedgerRepository implements StockLedgerRepository {

    private final JdbcClient jdbc;

    JdbcStockLedgerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(StockMovement m) {
        jdbc.sql("""
                INSERT INTO stock_movement (
                    id, brewery_id, lot_id, ingredient_id, type, quantity, on_hand_delta, reserved_delta,
                    reference, reason, occurred_at, actor_id)
                VALUES (:id, :brewery, :lot, :ingredient, :type, :qty, :onHand, :reserved, :reference, :reason,
                        :at, :actor)
                """)
                .param("id", m.id())
                .param("brewery", m.breweryId())
                .param("lot", m.lotId())
                .param("ingredient", m.ingredientId())
                .param("type", m.type().name())
                .param("qty", m.quantity())
                .param("onHand", m.onHandDelta())
                .param("reserved", m.reservedDelta())
                .param("reference", m.reference())
                .param("reason", m.reason())
                .param("at", Timestamp.from(m.occurredAt()))
                .param("actor", m.actorId())
                .update();
    }

    @Override
    public StockBalance balance(UUID breweryId, UUID lotId) {
        return jdbc.sql("""
                SELECT COALESCE(SUM(on_hand_delta), 0) AS on_hand, COALESCE(SUM(reserved_delta), 0) AS reserved
                FROM stock_movement WHERE brewery_id = :brewery AND lot_id = :lot
                """)
                .param("brewery", breweryId).param("lot", lotId)
                .query((rs, n) -> new StockBalance(rs.getBigDecimal("on_hand"), rs.getBigDecimal("reserved")))
                .single();
    }

    @Override
    public List<StockMovement> findByLot(UUID breweryId, UUID lotId) {
        return jdbc.sql("""
                SELECT id, brewery_id, lot_id, ingredient_id, type, quantity, reference, reason, occurred_at,
                       actor_id
                FROM stock_movement WHERE brewery_id = :brewery AND lot_id = :lot ORDER BY occurred_at DESC
                """)
                .param("brewery", breweryId).param("lot", lotId)
                .query((rs, n) -> map(rs))
                .list();
    }

    private static StockMovement map(ResultSet rs) throws SQLException {
        return StockMovement.reconstitute(
                rs.getObject("id", UUID.class),
                rs.getObject("brewery_id", UUID.class),
                rs.getObject("lot_id", UUID.class),
                rs.getObject("ingredient_id", UUID.class),
                StockMovementType.valueOf(rs.getString("type")),
                rs.getBigDecimal("quantity"),
                rs.getObject("reference", UUID.class),
                rs.getString("reason"),
                rs.getTimestamp("occurred_at").toInstant(),
                rs.getObject("actor_id", UUID.class));
    }
}
