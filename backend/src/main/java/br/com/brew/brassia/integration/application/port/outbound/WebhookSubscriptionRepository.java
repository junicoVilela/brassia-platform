package br.com.brew.brassia.integration.application.port.outbound;

import br.com.brew.brassia.integration.domain.WebhookEventType;
import br.com.brew.brassia.integration.domain.WebhookSubscription;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistência das assinaturas (INT-002). */
public interface WebhookSubscriptionRepository {

    void insert(WebhookSubscription subscription);

    boolean updateStatus(WebhookSubscription subscription, long expectedVersion);

    Optional<WebhookSubscription> byId(UUID breweryId, UUID subscriptionId);

    List<WebhookSubscription> findAll(UUID breweryId);

    /**
     * Assinaturas ativas de uma cervejaria interessadas num tipo de evento.
     *
     * <p>Sem {@code breweryId} isto seria um vazamento entre cervejarias com aparência de funcionalidade:
     * o webhook de uma cervejaria receberia os eventos de outra.
     */
    List<WebhookSubscription> activeFor(UUID breweryId, WebhookEventType eventType);
}
