package br.com.brew.brassia.integration.adapter.outbound.persistence;

import br.com.brew.brassia.integration.application.port.outbound.WebhookDeliveryRepository;
import br.com.brew.brassia.integration.domain.DeliveryStatus;
import br.com.brew.brassia.integration.domain.WebhookDelivery;
import br.com.brew.brassia.integration.domain.WebhookEventType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** O outbox das entregas em PostgreSQL (INT-002). */
@Repository
class JdbcWebhookDeliveryRepository implements WebhookDeliveryRepository {

    private static final String COLUMNS = """
            id, brewery_id, subscription_id, event_type, event_id, payload, status, attempts,
            next_attempt_at, delivered_at, last_response_status, last_error, created_at
            """;

    private final JdbcClient jdbc;

    JdbcWebhookDeliveryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Enfileira ignorando repetição do mesmo fato para a mesma assinatura.
     *
     * <p>O conflito é silencioso porque repetição aqui não é erro: o mesmo comando executado de novo, ou
     * o mesmo evento de domínio processado por dois nós, não deve produzir dois webhooks. Quem recebe não
     * tem como distinguir "o mesmo fato duas vezes" de "dois fatos iguais".
     */
    @Override
    public boolean enqueueIfAbsent(WebhookDelivery delivery) {
        return jdbc.sql("""
                INSERT INTO webhook_delivery (id, brewery_id, subscription_id, event_type, event_id,
                        payload, status, attempts, next_attempt_at, delivered_at, last_response_status,
                        last_error, created_at)
                VALUES (:id, :brewery, :subscription, :type, :event, :payload, :status, :attempts,
                        :next, NULL, NULL, NULL, :created)
                ON CONFLICT ON CONSTRAINT uq_delivery_event DO NOTHING
                """)
                .param("id", delivery.id())
                .param("brewery", delivery.breweryId())
                .param("subscription", delivery.subscriptionId())
                .param("type", delivery.eventType().externalName())
                .param("event", delivery.eventId())
                .param("payload", delivery.payload())
                .param("status", delivery.status().name())
                .param("attempts", delivery.attempts())
                .param("next", delivery.nextAttemptAt() == null
                        ? null : Timestamp.from(delivery.nextAttemptAt()))
                .param("created", Timestamp.from(delivery.createdAt()))
                .update() == 1;
    }

    /**
     * Grava o desfecho da tentativa.
     *
     * <p><strong>O {@code :brewery} do {@code WHERE} não estava ligado</strong>, e toda chamada terminava
     * em {@code InvalidDataAccessApiUsageException}. O efeito era o oposto exato do que o outbox promete:
     * a entrega saía, o destino recebia, o desfecho não era gravado, a linha continuava {@code PENDING} e
     * o mesmo evento era despachado de novo a cada rodada — para sempre. {@code enqueueIfAbsent} e
     * {@code FOR UPDATE SKIP LOCKED} existem para impedir duplicata, e ela entrava pela porta seguinte.
     *
     * <p>Pior: a exceção escapava do {@code catch} por entrega do despachante, porque o próprio
     * {@code catch} chama este método. A rodada inteira abortava na primeira entrega, e nenhum webhook da
     * instalação era concluído. O sintoma visível era uma linha de WARN a cada quinze segundos.
     */
    @Override
    public void update(WebhookDelivery delivery) {
        jdbc.sql("""
                UPDATE webhook_delivery
                SET status = :status, attempts = :attempts, next_attempt_at = :next,
                    delivered_at = :delivered, last_response_status = :responseStatus,
                    last_error = :error
                WHERE id = :id AND brewery_id = :brewery
                """)
                .param("status", delivery.status().name())
                .param("attempts", delivery.attempts())
                .param("next", delivery.nextAttemptAt() == null
                        ? null : Timestamp.from(delivery.nextAttemptAt()))
                .param("delivered", delivery.deliveredAt() == null
                        ? null : Timestamp.from(delivery.deliveredAt()))
                .param("responseStatus", delivery.lastResponseStatus())
                .param("error", delivery.lastError())
                .param("id", delivery.id())
                .param("brewery", delivery.breweryId())
                .update();
    }

    /**
     * Pega as entregas devidas travando as linhas para este processo.
     *
     * <p><strong>{@code FOR UPDATE SKIP LOCKED} é o que torna o despachante seguro com mais de uma
     * instância.</strong> Sem ele, duas instâncias selecionariam as mesmas linhas e o destino receberia o
     * evento em duplicidade — e quem recebe não distingue isso de dois fatos iguais. O {@code SKIP LOCKED}
     * faz a segunda instância pular o que a primeira pegou em vez de esperar por ela, o que mantém as duas
     * trabalhando em vez de uma bloqueada.
     *
     * <p>O travamento vale enquanto durar a transação de quem chamou — por isso o despachante roda a
     * rodada inteira dentro de uma.
     */
    @Override
    public List<WebhookDelivery> claimDue(Instant now, int limit) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM webhook_delivery "
                        + "WHERE status = 'PENDING' AND next_attempt_at <= :now "
                        + "ORDER BY next_attempt_at "
                        + "LIMIT :limit FOR UPDATE SKIP LOCKED")
                .param("now", Timestamp.from(now)).param("limit", limit)
                .query(this::map).list();
    }

    @Override
    public List<WebhookDelivery> recentOf(UUID breweryId, UUID subscriptionId, int limit) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM webhook_delivery "
                        + "WHERE brewery_id = :brewery AND subscription_id = :subscription "
                        + "ORDER BY created_at DESC LIMIT :limit")
                .param("brewery", breweryId).param("subscription", subscriptionId).param("limit", limit)
                .query(this::map).list();
    }

    private WebhookDelivery map(ResultSet rs, int rowNum) throws SQLException {
        var next = rs.getTimestamp("next_attempt_at");
        var delivered = rs.getTimestamp("delivered_at");
        var responseStatus = rs.getObject("last_response_status", Integer.class);
        return WebhookDelivery.reconstitute(
                rs.getObject("id", UUID.class),
                rs.getObject("brewery_id", UUID.class),
                rs.getObject("subscription_id", UUID.class),
                WebhookEventType.of(rs.getString("event_type")),
                rs.getString("event_id"),
                rs.getString("payload"),
                DeliveryStatus.valueOf(rs.getString("status")),
                rs.getInt("attempts"),
                next == null ? null : next.toInstant(),
                delivered == null ? null : delivered.toInstant(),
                responseStatus,
                rs.getString("last_error"),
                rs.getTimestamp("created_at").toInstant());
    }
}
