package br.com.brew.brassia.sales.adapter.outbound.persistence;

import br.com.brew.brassia.sales.application.port.outbound.PaymentRepository;
import br.com.brew.brassia.sales.domain.AlreadyReversedException;
import br.com.brew.brassia.sales.domain.Payment;
import br.com.brew.brassia.shared.money.Money;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcPaymentRepository implements PaymentRepository {

    private static final String COLUMNS = """
            id, brewery_id, order_id, amount, currency, received_on, method, note, recorded_by,
            recorded_at, reverses_payment_id
            """;

    private final JdbcClient jdbc;

    JdbcPaymentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(Payment p) {
        try {
            jdbc.sql("""
                    INSERT INTO sales_payment (id, brewery_id, order_id, amount, currency, received_on,
                            method, note, recorded_by, recorded_at, reverses_payment_id)
                    VALUES (:id, :brewery, :order, :amount, :currency, :receivedOn, :method, :note,
                            :by, :at, :reverses)
                    """)
                    .param("id", p.id()).param("brewery", p.breweryId()).param("order", p.orderId())
                    .param("amount", p.amount().toMinorUnit())
                    .param("currency", p.amount().currency())
                    .param("receivedOn", p.receivedOn()).param("method", p.method())
                    .param("note", p.note().orElse(null)).param("by", p.recordedBy())
                    .param("at", Timestamp.from(p.recordedAt()))
                    .param("reverses", p.reversesPaymentId().orElse(null))
                    .update();
        } catch (DuplicateKeyException jaEstornado) {
            // O índice único é a garantia: estornar duas vezes o mesmo lançamento tiraria da conta um
            // dinheiro que só entrou uma vez, e o cliente ganharia limite que não tem.
            throw new AlreadyReversedException();
        }
    }

    @Override
    public Optional<Payment> find(UUID breweryId, UUID paymentId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM sales_payment WHERE id = :id "
                        + "AND brewery_id = :brewery")
                .param("id", paymentId).param("brewery", breweryId)
                .query(JdbcPaymentRepository::map).optional();
    }

    @Override
    public List<Payment> ofOrder(UUID breweryId, UUID orderId) {
        return jdbc.sql("SELECT " + COLUMNS + """
                 FROM sales_payment WHERE brewery_id = :brewery AND order_id = :order
                ORDER BY recorded_at
                """)
                .param("brewery", breweryId).param("order", orderId)
                .query(JdbcPaymentRepository::map).list();
    }

    private static Payment map(ResultSet rs, int row) throws SQLException {
        return Payment.reconstitute(rs.getObject("id", UUID.class),
                rs.getObject("brewery_id", UUID.class), rs.getObject("order_id", UUID.class),
                new Money(rs.getBigDecimal("amount"), rs.getString("currency")),
                rs.getObject("received_on", LocalDate.class), rs.getString("method"),
                rs.getString("note"), rs.getObject("recorded_by", UUID.class),
                rs.getTimestamp("recorded_at").toInstant(),
                rs.getObject("reverses_payment_id", UUID.class));
    }
}
