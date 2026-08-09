package br.com.brew.brassia.integration.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Uma entrega no outbox (INT-002).
 *
 * <p><strong>Por que existe uma linha em vez de um POST direto.</strong> Mandar o webhook dentro do caso
 * de uso ligaria o domínio à disponibilidade de um servidor de terceiro: a liberação de uma OP passaria a
 * demorar o timeout de um endpoint fora do ar, e — pior — uma falha na entrega tenderia a derrubar a
 * transação, fazendo o critério "falha não bloqueia domínio" ser exatamente o oposto do que acontece.
 * Aqui o comando grava a intenção de entregar <em>no mesmo commit</em> em que grava o próprio fato, e quem
 * entrega é outro processo, depois.
 *
 * <p>A consequência que importa: se a transação do comando reverter, a entrega reverte junto. Sem o
 * outbox, um webhook "ordem liberada" sairia para uma ordem que não existe — e não há como desmandar.
 */
public final class WebhookDelivery {

    /**
     * Quantas vezes tentar antes de desistir.
     *
     * <p>Cinco tentativas com backoff exponencial cobrem cerca de meia hora, que é a ordem de grandeza de
     * um deploy ou de uma queda curta do outro lado. Mais que isso deixaria de ser "o servidor piscou" e
     * passaria a ser "o endereço está errado" — e insistir num endereço errado por dias só produz ruído.
     */
    public static final int MAX_ATTEMPTS = 5;

    private static final Duration BASE_BACKOFF = Duration.ofSeconds(30);

    private final UUID id;
    private final UUID breweryId;
    private final UUID subscriptionId;
    private final WebhookEventType eventType;
    /** Identidade do fato que originou a entrega — é o que torna a repetição reconhecível. */
    private final String eventId;
    private final String payload;
    private final DeliveryStatus status;
    private final int attempts;
    private final Instant nextAttemptAt;
    private final Instant deliveredAt;
    private final Integer lastResponseStatus;
    private final String lastError;
    private final Instant createdAt;

    private WebhookDelivery(UUID id, UUID breweryId, UUID subscriptionId, WebhookEventType eventType,
            String eventId, String payload, DeliveryStatus status, int attempts, Instant nextAttemptAt,
            Instant deliveredAt, Integer lastResponseStatus, String lastError, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.subscriptionId = Objects.requireNonNull(subscriptionId, "subscriptionId");
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.payload = Objects.requireNonNull(payload, "payload");
        this.status = Objects.requireNonNull(status, "status");
        this.attempts = attempts;
        this.nextAttemptAt = nextAttemptAt;
        this.deliveredAt = deliveredAt;
        this.lastResponseStatus = lastResponseStatus;
        this.lastError = lastError;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /** Enfileira uma entrega. Elegível imediatamente — o backoff só existe depois de uma falha. */
    public static WebhookDelivery enqueue(UUID breweryId, UUID subscriptionId, WebhookEventType eventType,
            String eventId, String payload, Instant now) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("identificador do evento é obrigatório");
        }
        return new WebhookDelivery(UUID.randomUUID(), breweryId, subscriptionId, eventType, eventId.trim(),
                payload, DeliveryStatus.PENDING, 0, now, null, null, null, now);
    }

    public static WebhookDelivery reconstitute(UUID id, UUID breweryId, UUID subscriptionId,
            WebhookEventType eventType, String eventId, String payload, DeliveryStatus status, int attempts,
            Instant nextAttemptAt, Instant deliveredAt, Integer lastResponseStatus, String lastError,
            Instant createdAt) {
        return new WebhookDelivery(id, breweryId, subscriptionId, eventType, eventId, payload, status,
                attempts, nextAttemptAt, deliveredAt, lastResponseStatus, lastError, createdAt);
    }

    public WebhookDelivery succeededWith(int responseStatus, Instant now) {
        return new WebhookDelivery(id, breweryId, subscriptionId, eventType, eventId, payload,
                DeliveryStatus.DELIVERED, attempts + 1, null, now, responseStatus, null, createdAt);
    }

    /**
     * Registra a falha e agenda a próxima tentativa, ou desiste.
     *
     * <p>O backoff é exponencial (30 s, 1 min, 2 min, 4 min, …) por uma razão específica: um destino que
     * caiu costuma voltar, e martelá-lo a cada 30 segundos atrapalha justamente a recuperação dele. Com
     * muitas cervejarias e um destino comum, retry fixo vira uma pequena negação de serviço involuntária.
     *
     * <p>O motivo é guardado <strong>truncado e sem corpo de resposta</strong>. Um erro de terceiro pode
     * conter qualquer coisa — inclusive o que mandamos —, e a coluna de erro de uma integração não é lugar
     * para eco de dado da cervejaria.
     */
    public WebhookDelivery failedWith(Integer responseStatus, String reason, Instant now) {
        var nextAttempts = attempts + 1;
        var safeReason = truncate(reason);
        if (nextAttempts >= MAX_ATTEMPTS) {
            return new WebhookDelivery(id, breweryId, subscriptionId, eventType, eventId, payload,
                    DeliveryStatus.EXHAUSTED, nextAttempts, null, null, responseStatus, safeReason,
                    createdAt);
        }
        var delay = BASE_BACKOFF.multipliedBy(1L << (nextAttempts - 1));
        return new WebhookDelivery(id, breweryId, subscriptionId, eventType, eventId, payload,
                DeliveryStatus.PENDING, nextAttempts, now.plus(delay), null, responseStatus, safeReason,
                createdAt);
    }

    /**
     * Desiste agora, sem gastar as tentativas restantes.
     *
     * <p>Existe porque há motivos de falha que não melhoram com o tempo: a assinatura foi revogada entre o
     * enfileiramento e a entrega. Tentar mais quatro vezes ao longo de meia hora seria insistir em algo que
     * já se sabe que não deve acontecer — e deixaria a fila ocupada com trabalho decidido.
     */
    public WebhookDelivery abandonedBecause(String reason, Instant now) {
        return new WebhookDelivery(id, breweryId, subscriptionId, eventType, eventId, payload,
                DeliveryStatus.EXHAUSTED, attempts + 1, null, null, null, truncate(reason), createdAt);
    }

    private static String truncate(String reason) {
        if (reason == null || reason.isBlank()) {
            return "sem detalhe";
        }
        var trimmed = reason.trim();
        return trimmed.length() <= 200 ? trimmed : trimmed.substring(0, 200);
    }

    public boolean isDue(Instant now) {
        return status == DeliveryStatus.PENDING && nextAttemptAt != null && !nextAttemptAt.isAfter(now);
    }

    public UUID id() { return id; }
    public UUID breweryId() { return breweryId; }
    public UUID subscriptionId() { return subscriptionId; }
    public WebhookEventType eventType() { return eventType; }
    public String eventId() { return eventId; }
    public String payload() { return payload; }
    public DeliveryStatus status() { return status; }
    public int attempts() { return attempts; }
    public Instant nextAttemptAt() { return nextAttemptAt; }
    public Instant deliveredAt() { return deliveredAt; }
    public Integer lastResponseStatus() { return lastResponseStatus; }
    public String lastError() { return lastError; }
    public Instant createdAt() { return createdAt; }
}
