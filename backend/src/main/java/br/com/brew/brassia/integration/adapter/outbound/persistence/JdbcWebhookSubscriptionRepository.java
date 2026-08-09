package br.com.brew.brassia.integration.adapter.outbound.persistence;

import br.com.brew.brassia.integration.application.port.outbound.WebhookSubscriptionRepository;
import br.com.brew.brassia.integration.domain.SubscriptionStatus;
import br.com.brew.brassia.integration.domain.WebhookEventType;
import br.com.brew.brassia.integration.domain.WebhookSubscription;
import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Assinaturas de webhook em PostgreSQL (INT-002). */
@Repository
class JdbcWebhookSubscriptionRepository implements WebhookSubscriptionRepository {

    private static final String COLUMNS = """
            id, brewery_id, name, endpoint, secret, events, status, created_by, created_at, version
            """;

    private final JdbcClient jdbc;

    JdbcWebhookSubscriptionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(WebhookSubscription subscription) {
        jdbc.sql("""
                INSERT INTO webhook_subscription (id, brewery_id, name, endpoint, secret, events, status,
                        created_by, created_at, version)
                VALUES (:id, :brewery, :name, :endpoint, :secret, :events, :status, :by, :at, :version)
                """)
                .param("id", subscription.id())
                .param("brewery", subscription.breweryId())
                .param("name", subscription.name())
                .param("endpoint", subscription.endpoint().toString())
                .param("secret", subscription.secretForPersistence())
                .param("events", subscription.events().stream()
                        .map(WebhookEventType::externalName).collect(Collectors.joining(",")))
                .param("status", subscription.status().name())
                .param("by", subscription.createdBy())
                .param("at", Timestamp.from(subscription.createdAt()))
                .param("version", subscription.version())
                .update();
    }

    @Override
    public boolean updateStatus(WebhookSubscription subscription, long expectedVersion) {
        return jdbc.sql("""
                UPDATE webhook_subscription SET status = :status, version = version + 1
                WHERE id = :id AND brewery_id = :brewery AND version = :expected
                """)
                .param("status", subscription.status().name())
                .param("id", subscription.id())
                .param("brewery", subscription.breweryId())
                .param("expected", expectedVersion)
                .update() == 1;
    }

    @Override
    public Optional<WebhookSubscription> byId(UUID breweryId, UUID subscriptionId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM webhook_subscription "
                        + "WHERE brewery_id = :brewery AND id = :id")
                .param("brewery", breweryId).param("id", subscriptionId)
                .query(this::map).optional();
    }

    @Override
    public List<WebhookSubscription> findAll(UUID breweryId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM webhook_subscription "
                        + "WHERE brewery_id = :brewery ORDER BY created_at DESC")
                .param("brewery", breweryId)
                .query(this::map).list();
    }

    /**
     * Ativas de uma cervejaria interessadas num tipo.
     *
     * <p>O filtro por tipo é feito em memória sobre um conjunto que já é pequeno (assinaturas ativas de
     * uma cervejaria) em vez de com {@code LIKE} sobre a coluna de texto. Um {@code LIKE '%recipe.published%'}
     * casaria também com um tipo futuro chamado {@code recipe.published_draft} — o tipo de defeito que só
     * aparece quando alguém acrescenta a constante nova, meses depois.
     */
    @Override
    public List<WebhookSubscription> activeFor(UUID breweryId, WebhookEventType eventType) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM webhook_subscription "
                        + "WHERE brewery_id = :brewery AND status = 'ACTIVE'")
                .param("brewery", breweryId)
                .query(this::map).list().stream()
                .filter(s -> s.subscribesTo(eventType))
                .toList();
    }

    private WebhookSubscription map(ResultSet rs, int rowNum) throws SQLException {
        return WebhookSubscription.reconstitute(
                rs.getObject("id", UUID.class),
                rs.getObject("brewery_id", UUID.class),
                rs.getString("name"),
                URI.create(rs.getString("endpoint")),
                rs.getString("secret"),
                parseEvents(rs.getString("events")),
                SubscriptionStatus.valueOf(rs.getString("status")),
                rs.getObject("created_by", UUID.class),
                rs.getTimestamp("created_at").toInstant(),
                rs.getLong("version"));
    }

    /**
     * Tipo desconhecido na coluna é <strong>ignorado</strong>, não fatal.
     *
     * <p>É o cenário de rollback: uma versão futura acrescenta um tipo, alguém assina com ele, e a
     * aplicação volta para esta versão. Explodir aqui derrubaria a leitura da assinatura inteira — e com
     * ela os tipos que esta versão conhece perfeitamente.
     */
    private static Set<WebhookEventType> parseEvents(String raw) {
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .flatMap(s -> {
                    try {
                        return java.util.stream.Stream.of(WebhookEventType.of(s));
                    } catch (IllegalArgumentException e) {
                        return java.util.stream.Stream.empty();
                    }
                })
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
