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

    private static final String CREDITO = """
            SELECT ceiling_amount, currency FROM sales_customer_credit
            WHERE brewery_id = :brewery AND customer_id = :customer
            """;

    @Override
    public CreditLimit creditOf(UUID breweryId, UUID customerId) {
        return leTeto(CREDITO, breweryId, customerId);
    }

    @Override
    public CreditLimit lockCreditOf(UUID breweryId, UUID customerId) {
        // `FOR UPDATE` sem `NOWAIT`: a segunda venda para o mesmo cliente ESPERA a primeira commitar e
        // então relê o comprometido já com ela dentro. Recusar de imediato com "tente de novo" seria
        // devolver ao vendedor um erro que não é dele, num caso em que a espera é de milissegundos.
        //
        // Sem linha não há trava — e não há o que proteger: cliente sem teto cadastrado não tem limite
        // para furar. `creditOf` devolveria o mesmo `CreditLimit.none()`.
        return leTeto(CREDITO + "FOR UPDATE\n", breweryId, customerId);
    }

    /**
     * A leitura do teto, uma só vez.
     *
     * <p>As duas versões diferem apenas pelo `FOR UPDATE`. Duplicá-las deixaria o dia em que o teto ganhar
     * uma coluna — vigência, moeda por canal — passar com só uma delas atualizada: a tela do portal
     * mostraria o limite novo e a conferência da venda continuaria cobrando o antigo, calada.
     *
     * <p>Ausência de linha vira {@link CreditLimit#none()}, e não vazio: "sem limite" é um estado da
     * política, e o comportamento seguro vira o padrão em vez de uma checagem esquecível.
     */
    private CreditLimit leTeto(String sql, UUID breweryId, UUID customerId) {
        return jdbc.sql(sql)
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
        // AGORA MEDE RECEBÍVEL (DEB-SAL-002, resolvido): pedidos confirmados E entregues, menos o que
        // já foi recebido. Cancelado continua fora — ele nunca virou dívida.
        //
        // O pagamento parcial conta proporcionalmente: ignorá-lo faria um cliente que pagou 90% ocupar o
        // limite inteiro, e ele deixaria de comprar por uma dívida que não tem.
        //
        // GREATEST(…, 0) por linha de pedido: um cliente que pagou a mais num pedido não ganha limite
        // extra nos outros — o crédito a maior é assunto do financeiro, e compensá-lo aqui esconderia
        // um erro de lançamento dentro de um número de crédito.
        return jdbc.sql("""
                SELECT COALESCE(SUM(GREATEST(devido - recebido, 0)), 0) FROM (
                    SELECT o.id,
                           COALESCE(SUM(l.quantity * l.unit_amount), 0) AS devido,
                           COALESCE((SELECT SUM(CASE WHEN p.reverses_payment_id IS NULL
                                                     THEN p.amount ELSE -p.amount END)
                                     FROM sales_payment p
                                     WHERE p.order_id = o.id AND p.currency = :currency), 0) AS recebido
                    FROM sales_order o
                    JOIN sales_order_line l ON l.order_id = o.id AND l.brewery_id = o.brewery_id
                    WHERE o.brewery_id = :brewery AND o.customer_id = :customer
                      AND o.status IN ('PLACED', 'FULFILLED') AND l.currency = :currency
                    GROUP BY o.id
                ) por_pedido
                """)
                .param("brewery", breweryId).param("customer", customerId).param("currency", currency)
                .query(BigDecimal.class).single();
    }
}
