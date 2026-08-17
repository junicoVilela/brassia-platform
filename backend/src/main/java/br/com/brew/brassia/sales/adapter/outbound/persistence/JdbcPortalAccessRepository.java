package br.com.brew.brassia.sales.adapter.outbound.persistence;

import br.com.brew.brassia.sales.application.port.outbound.PortalAccessRepository;
import br.com.brew.brassia.sales.domain.CreditLimit;
import br.com.brew.brassia.shared.money.Money;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcPortalAccessRepository implements PortalAccessRepository {

    private final JdbcClient jdbc;

    JdbcPortalAccessRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void grant(UUID breweryId, UUID userId, UUID customerId, UUID channelId, UUID actorId) {
        jdbc.sql("""
                INSERT INTO sales_portal_user (user_id, brewery_id, customer_id, channel_id, granted_by,
                                               granted_at)
                VALUES (:user, :brewery, :customer, :channel, :by, :at)
                ON CONFLICT (user_id) DO UPDATE
                SET customer_id = :customer, channel_id = :channel, granted_by = :by, granted_at = :at
                """)
                .param("user", userId).param("brewery", breweryId).param("customer", customerId)
                .param("channel", channelId).param("by", actorId)
                .param("at", Timestamp.from(Instant.now()))
                .update();
    }

    @Override
    public void revoke(UUID breweryId, UUID userId) {
        jdbc.sql("DELETE FROM sales_portal_user WHERE user_id = :user AND brewery_id = :brewery")
                .param("user", userId).param("brewery", breweryId)
                .update();
    }

    @Override
    public Optional<PortalAccess> findAccess(UUID userId) {
        // Busca só pelo usuário, e a cervejaria vem da linha — e não do principal. É o vínculo que diz
        // de qual cervejaria e de qual cliente ele é; aceitar os dois de fora permitiria a combinação
        // errada passar.
        return jdbc.sql("""
                SELECT user_id, brewery_id, customer_id, channel_id
                FROM sales_portal_user WHERE user_id = :user
                """)
                .param("user", userId)
                .query((rs, row) -> new PortalAccess(rs.getObject("user_id", UUID.class),
                        rs.getObject("brewery_id", UUID.class),
                        rs.getObject("customer_id", UUID.class),
                        rs.getObject("channel_id", UUID.class)))
                .optional();
    }

    @Override
    public CreditLimit creditOf(UUID breweryId, UUID customerId) {
        // Ausência de linha vira CreditLimit.none(), e não vazio: "sem limite" é um estado da política,
        // e o comportamento seguro vira o padrão em vez de uma checagem esquecível.
        return jdbc.sql("""
                SELECT ceiling_amount, currency FROM sales_customer_credit
                WHERE brewery_id = :brewery AND customer_id = :customer
                """)
                .param("brewery", breweryId).param("customer", customerId)
                .query((rs, row) -> CreditLimit.of(
                        new Money(rs.getBigDecimal("ceiling_amount"), rs.getString("currency"))))
                .optional()
                .orElseGet(CreditLimit::none);
    }

    @Override
    public void setCredit(UUID breweryId, UUID customerId, BigDecimal ceiling, String currency,
            UUID actorId) {
        jdbc.sql("""
                INSERT INTO sales_customer_credit (customer_id, brewery_id, ceiling_amount, currency,
                                                   updated_by, updated_at)
                VALUES (:customer, :brewery, :ceiling, :currency, :by, :at)
                ON CONFLICT (customer_id) DO UPDATE
                SET ceiling_amount = :ceiling, currency = :currency, updated_by = :by, updated_at = :at
                """)
                .param("customer", customerId).param("brewery", breweryId).param("ceiling", ceiling)
                .param("currency", currency).param("by", actorId)
                .param("at", Timestamp.from(Instant.now()))
                .update();
    }

    @Override
    public BigDecimal committedAmount(UUID breweryId, UUID customerId, String currency) {
        // Só PLACED. Cancelado nunca contou, e entregue sai da conta — o que é a limitação declarada
        // em DEB-SAL-002, e não um esquecimento.
        return jdbc.sql("""
                SELECT COALESCE(SUM(l.quantity * l.unit_amount), 0)
                FROM sales_order o
                JOIN sales_order_line l ON l.order_id = o.id AND l.brewery_id = o.brewery_id
                WHERE o.brewery_id = :brewery AND o.customer_id = :customer
                  AND o.status = 'PLACED' AND l.currency = :currency
                """)
                .param("brewery", breweryId).param("customer", customerId).param("currency", currency)
                .query(BigDecimal.class).single();
    }
}
