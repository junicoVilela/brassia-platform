package br.com.brew.brassia.integration.application.service;

import br.com.brew.brassia.integration.application.port.outbound.WebhookDeliveryRepository;
import br.com.brew.brassia.integration.application.port.outbound.WebhookSubscriptionRepository;
import br.com.brew.brassia.integration.domain.WebhookDelivery;
import br.com.brew.brassia.integration.domain.WebhookEventType;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Põe o evento no outbox (INT-002).
 *
 * <p><strong>Este método roda dentro da transação do comando, e é isso que faz o outbox valer.</strong>
 * Se a liberação da OP reverter, a entrega reverte junto — e o webhook "ordem liberada" não sai para uma
 * ordem que não existe. Um webhook não se desmanda; a única defesa é ele nunca ter saído.
 *
 * <p>O simétrico também vale: gravado o fato, a intenção de entregar está gravada com ele. Nenhum evento
 * se perde porque a aplicação caiu entre o commit e o envio — quem envia lê do banco, depois.
 */
public final class EventEnqueuer {

    private final WebhookSubscriptionRepository subscriptions;
    private final WebhookDeliveryRepository deliveries;
    private final Clock clock;

    public EventEnqueuer(WebhookSubscriptionRepository subscriptions,
            WebhookDeliveryRepository deliveries, Clock clock) {
        this.subscriptions = Objects.requireNonNull(subscriptions, "subscriptions");
        this.deliveries = Objects.requireNonNull(deliveries, "deliveries");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Enfileira o evento para cada assinatura interessada.
     *
     * <p>Nenhuma assinatura interessada é o caso comum — a maioria das instalações não usa webhook — e não
     * é erro nem log: é simplesmente nada a fazer.
     *
     * @param eventId identidade do fato. É o que torna a repetição reconhecível pelos dois lados: viaja no
     *                cabeçalho {@code X-Brassia-Event-Id} para que o destino possa deduplicar o nosso
     *                retry, e sustenta a restrição única do outbox deste lado.
     */
    public int enqueue(UUID breweryId, WebhookEventType type, String eventId, String payload) {
        Objects.requireNonNull(breweryId, "breweryId");
        Objects.requireNonNull(type, "type");

        var now = clock.instant();
        var enqueued = 0;
        for (var subscription : subscriptions.activeFor(breweryId, type)) {
            var delivery = WebhookDelivery.enqueue(breweryId, subscription.id(), type, eventId, payload, now);
            if (deliveries.enqueueIfAbsent(delivery)) {
                enqueued++;
            }
        }
        return enqueued;
    }
}
