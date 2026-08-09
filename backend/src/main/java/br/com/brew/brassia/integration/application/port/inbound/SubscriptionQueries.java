package br.com.brew.brassia.integration.application.port.inbound;

import br.com.brew.brassia.integration.domain.WebhookDelivery;
import br.com.brew.brassia.integration.domain.WebhookSubscription;
import java.util.List;
import java.util.UUID;

/** Consultas de assinaturas e entregas (INT-002). */
public interface SubscriptionQueries {

    List<WebhookSubscription> subscriptions(UUID breweryId);

    /** Entregas recentes de uma assinatura — é onde se vê o que falhou e quantas vezes. */
    List<WebhookDelivery> deliveries(UUID breweryId, UUID subscriptionId, int limit);
}
