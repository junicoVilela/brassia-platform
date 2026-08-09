package br.com.brew.brassia.integration.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.integration.application.port.outbound.WebhookDeliveryRepository;
import br.com.brew.brassia.integration.application.port.outbound.WebhookSender;
import br.com.brew.brassia.integration.application.port.outbound.WebhookSubscriptionRepository;
import br.com.brew.brassia.integration.domain.DeliveryStatus;
import br.com.brew.brassia.integration.domain.SubscriptionStatus;
import br.com.brew.brassia.integration.domain.WebhookDelivery;
import br.com.brew.brassia.integration.domain.WebhookSubscription;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Tenta entregar o que está no outbox (INT-002).
 *
 * <p><strong>Aqui é onde "falha não bloqueia o domínio" deixa de ser intenção e vira estrutura.</strong>
 * Este código roda fora da transação do comando, num processo agendado; nada do que aconteça com o destino
 * — 500, timeout, DNS quebrado, certificado expirado — tem caminho de volta até a liberação da OP que
 * originou o evento. Ela já foi confirmada, gravada e respondida muito antes.
 *
 * <p>Uma entrega que falha não interrompe as outras. Não é tolerância a erro por conveniência: um destino
 * mal configurado de uma cervejaria não pode impedir as demais de receberem os eventos delas.
 */
public final class DeliveryDispatcher {

    /** Quantas entregas por rodada. Teto para que uma fila represada não monopolize a janela. */
    private static final int BATCH = 50;

    private final WebhookDeliveryRepository deliveries;
    private final WebhookSubscriptionRepository subscriptions;
    private final WebhookSender sender;
    private final AuditTrail audit;
    private final Clock clock;

    public DeliveryDispatcher(WebhookDeliveryRepository deliveries,
            WebhookSubscriptionRepository subscriptions, WebhookSender sender, AuditTrail audit,
            Clock clock) {
        this.deliveries = Objects.requireNonNull(deliveries, "deliveries");
        this.subscriptions = Objects.requireNonNull(subscriptions, "subscriptions");
        this.sender = Objects.requireNonNull(sender, "sender");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Uma rodada. Devolve quantas entregas foram tentadas. */
    public int dispatchDue() {
        var now = clock.instant();
        var due = deliveries.claimDue(now, BATCH);
        for (var delivery : due) {
            try {
                attempt(delivery, now);
            } catch (RuntimeException ex) {
                // Defeito nosso ao processar uma entrega não pode parar a fila inteira. A entrega
                // continua PENDING e volta na próxima rodada.
                deliveries.update(delivery.failedWith(null, "erro interno ao entregar", now));
            }
        }
        return due.size();
    }

    private void attempt(WebhookDelivery delivery, java.time.Instant now) {
        var subscription = subscriptions.byId(delivery.breweryId(), delivery.subscriptionId()).orElse(null);
        if (subscription == null || subscription.status() == SubscriptionStatus.REVOKED) {
            // Revogada entre o enfileiramento e a entrega. Desiste de uma vez em vez de gastar o backoff:
            // é um motivo que não melhora com o tempo. "Esgotada" é o estado honesto — a entrega não
            // aconteceu e não vai acontecer, e a linha fica para quem for investigar.
            deliveries.update(delivery.abandonedBecause("assinatura revogada", now));
            return;
        }

        var epochSeconds = now.getEpochSecond();
        var signature = subscription.sign(epochSeconds, delivery.payload());
        var result = sender.send(subscription.endpoint(), headers(delivery, epochSeconds, signature),
                delivery.payload());

        var updated = result.success()
                ? delivery.succeededWith(result.status(), now)
                : delivery.failedWith(result.status(), result.error(), now);
        deliveries.update(updated);

        auditIfTerminal(subscription, updated);
    }


    /**
     * Os cabeçalhos da entrega.
     *
     * <p>O tipo do evento e a identidade dele viajam em cabeçalho <em>além</em> de estarem no corpo: quem
     * recebe consegue rotear e deduplicar sem desserializar o payload, e a deduplicação do outro lado é o
     * que torna seguro o nosso retry. O segredo nunca aparece — só a assinatura derivada dele.
     */
    private Map<String, String> headers(WebhookDelivery delivery, long epochSeconds, String signature) {
        var headers = new LinkedHashMap<String, String>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Brassia-Event", delivery.eventType().externalName());
        headers.put("X-Brassia-Event-Id", delivery.eventId());
        headers.put("X-Brassia-Delivery-Id", delivery.id().toString());
        headers.put("X-Brassia-Timestamp", String.valueOf(epochSeconds));
        headers.put("X-Brassia-Signature", signature);
        return headers;
    }

    /**
     * Audita o desfecho, não cada tentativa.
     *
     * <p>Auditar toda tentativa encheria a trilha com o retry de um destino instável — e o retry é
     * comportamento esperado, não fato a guardar. O que a história pede ("destino e tentativas são
     * auditados") é o resultado: entregou depois de quantas tentativas, ou desistiu depois de quantas.
     *
     * <p>O endereço vai como host, sem caminho nem query: o caminho de um webhook às vezes carrega token,
     * e a trilha de auditoria não é lugar para ele.
     */
    private void auditIfTerminal(WebhookSubscription subscription, WebhookDelivery delivery) {
        if (!delivery.status().isTerminal()) {
            return;
        }
        var metadata = new LinkedHashMap<String, String>();
        metadata.put("subscription", subscription.name());
        metadata.put("host", subscription.endpoint().getHost());
        metadata.put("event", delivery.eventType().externalName());
        metadata.put("eventId", delivery.eventId());
        metadata.put("attempts", String.valueOf(delivery.attempts()));
        metadata.put("outcome", delivery.status().name());
        if (delivery.lastResponseStatus() != null) {
            metadata.put("responseStatus", String.valueOf(delivery.lastResponseStatus()));
        }
        if (delivery.status() == DeliveryStatus.EXHAUSTED && delivery.lastError() != null) {
            metadata.put("lastError", delivery.lastError());
        }
        audit.record(AuditEvent.success(delivery.breweryId(), null, "integration.webhook.delivery",
                "webhook_delivery", delivery.id().toString(), metadata));
    }
}
