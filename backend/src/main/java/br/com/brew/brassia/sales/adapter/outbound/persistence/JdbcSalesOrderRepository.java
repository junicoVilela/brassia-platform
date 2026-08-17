package br.com.brew.brassia.sales.adapter.outbound.persistence;

import br.com.brew.brassia.sales.application.port.outbound.SalesOrderRepository;
import br.com.brew.brassia.sales.domain.LotReservation;
import br.com.brew.brassia.shared.money.Money;
import br.com.brew.brassia.sales.domain.OrderLine;
import br.com.brew.brassia.sales.domain.OrderStatus;
import br.com.brew.brassia.sales.domain.SalesOrder;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSalesOrderRepository implements SalesOrderRepository {

    private final JdbcClient jdbc;

    JdbcSalesOrderRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(SalesOrder order, UUID actorId, String idempotencyKey) {
        jdbc.sql("""
                INSERT INTO sales_order (id, brewery_id, code, customer_id, channel_id, status, placed_on,
                                         promised_for, idempotency_key, created_by, created_at)
                VALUES (:id, :brewery, :code, :customer, :channel, :status, :placed, :promised, :key,
                        :by, :at)
                """)
                .param("id", order.id()).param("brewery", order.breweryId())
                .param("code", order.code()).param("customer", order.customerId())
                .param("channel", order.channelId()).param("status", order.status().name())
                .param("placed", Date.valueOf(order.placedOn()))
                .param("promised", order.promisedFor().map(Date::valueOf).orElse(null))
                .param("key", idempotencyKey)
                .param("by", actorId).param("at", Timestamp.from(order.createdAt()))
                .update();

        for (var line : order.lines()) {
            var lineId = UUID.randomUUID();
            jdbc.sql("""
                    INSERT INTO sales_order_line (id, brewery_id, order_id, product_id, sku, quantity,
                                                  unit_amount, currency, tax_included)
                    VALUES (:id, :brewery, :order, :product, :sku, :qty, :amount, :currency, :tax)
                    """)
                    .param("id", lineId).param("brewery", order.breweryId()).param("order", order.id())
                    .param("product", line.productId()).param("sku", line.sku())
                    .param("qty", line.quantity()).param("amount", line.unitPrice().amount())
                    .param("currency", line.unitPrice().currency()).param("tax", line.taxIncluded())
                    .update();

            for (var r : line.reservations()) {
                jdbc.sql("""
                        INSERT INTO sales_lot_reservation (id, brewery_id, order_line_id, finished_lot_id,
                                                           lot_code, units, best_before)
                        VALUES (:id, :brewery, :line, :lot, :code, :units, :bb)
                        """)
                        .param("id", UUID.randomUUID()).param("brewery", order.breweryId())
                        .param("line", lineId).param("lot", r.finishedLotId())
                        .param("code", r.lotCode()).param("units", r.units())
                        .param("bb", Date.valueOf(r.bestBefore()))
                        .update();
            }
        }
    }

    @Override
    public void updateStatusAndPromise(SalesOrder order) {
        jdbc.sql("""
                UPDATE sales_order SET status = :status, promised_for = :promised
                WHERE id = :id AND brewery_id = :brewery
                """)
                .param("status", order.status().name())
                .param("promised", order.promisedFor().map(Date::valueOf).orElse(null))
                .param("id", order.id()).param("brewery", order.breweryId())
                .update();
    }

    @Override
    public Optional<SalesOrder> find(UUID breweryId, UUID id) {
        return head(breweryId, "id = :id", "id", id);
    }

    @Override
    public Optional<SalesOrder> findByIdempotencyKey(UUID breweryId, String key) {
        return head(breweryId, "idempotency_key = :key", "key", key);
    }

    @Override
    public List<SalesOrder> list(UUID breweryId) {
        return jdbc.sql("""
                SELECT id FROM sales_order WHERE brewery_id = :brewery ORDER BY placed_on DESC, code
                """)
                .param("brewery", breweryId)
                .query(UUID.class).list().stream()
                .map(id -> find(breweryId, id).orElseThrow())
                .toList();
    }

    private Optional<SalesOrder> head(UUID breweryId, String where, String param, Object value) {
        return jdbc.sql("""
                SELECT id, brewery_id, code, customer_id, channel_id, status, placed_on, promised_for,
                       created_at
                FROM sales_order WHERE brewery_id = :brewery AND
                """ + where)
                .param("brewery", breweryId).param(param, value)
                .query((rs, row) -> {
                    var id = rs.getObject("id", UUID.class);
                    var promised = rs.getDate("promised_for");
                    return SalesOrder.reconstitute(id, rs.getObject("brewery_id", UUID.class),
                            rs.getObject("customer_id", UUID.class),
                            rs.getObject("channel_id", UUID.class), rs.getString("code"),
                            lines(breweryId, id), rs.getDate("placed_on").toLocalDate(),
                            promised == null ? null : promised.toLocalDate(),
                            OrderStatus.valueOf(rs.getString("status")),
                            rs.getTimestamp("created_at").toInstant());
                })
                .optional();
    }

    private List<OrderLine> lines(UUID breweryId, UUID orderId) {
        return jdbc.sql("""
                SELECT id, product_id, sku, quantity, unit_amount, currency, tax_included
                FROM sales_order_line WHERE brewery_id = :brewery AND order_id = :order ORDER BY sku
                """)
                .param("brewery", breweryId).param("order", orderId)
                .query((rs, row) -> new OrderLine(rs.getObject("product_id", UUID.class),
                        rs.getString("sku"), rs.getInt("quantity"),
                        new Money(rs.getBigDecimal("unit_amount"), rs.getString("currency")),
                        rs.getBoolean("tax_included"),
                        reservations(breweryId, rs.getObject("id", UUID.class))))
                .list();
    }

    private List<LotReservation> reservations(UUID breweryId, UUID lineId) {
        return jdbc.sql("""
                SELECT finished_lot_id, lot_code, units, best_before
                FROM sales_lot_reservation WHERE brewery_id = :brewery AND order_line_id = :line
                ORDER BY best_before
                """)
                .param("brewery", breweryId).param("line", lineId)
                .query((rs, row) -> new LotReservation(rs.getObject("finished_lot_id", UUID.class),
                        rs.getString("lot_code"), rs.getInt("units"),
                        rs.getDate("best_before").toLocalDate()))
                .list();
    }
}
