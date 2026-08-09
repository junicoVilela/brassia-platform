package br.com.brew.brassia.integration.application.port.inbound;

import br.com.brew.brassia.integration.domain.SubscriptionStatus;
import br.com.brew.brassia.integration.domain.WebhookSubscription;
import java.util.Set;
import java.util.UUID;

/** Cadastro e estado das assinaturas de webhook (INT-002). */
public interface SubscriptionCommands {

    /**
     * Cria a assinatura e devolve o segredo <strong>uma única vez</strong>.
     *
     * <p>É o mesmo raciocínio de uma API key: o segredo precisa chegar a quem vai configurar o outro lado,
     * e depois disso não há motivo legítimo para lê-lo de volta. Guardar um caminho de leitura seria
     * guardar uma porta que só serve para vazar — quem perdeu o segredo cria outra assinatura.
     */
    Created create(CreateRequest request);

    WebhookSubscription changeStatus(ChangeStatusRequest request);

    record CreateRequest(
            UUID actorId,
            UUID breweryId,
            String name,
            String endpoint,
            Set<String> events) {
    }

    record ChangeStatusRequest(
            UUID actorId,
            UUID breweryId,
            UUID subscriptionId,
            SubscriptionStatus target,
            long expectedVersion) {
    }

    /** A assinatura criada e o segredo em claro, que não volta a ser exposto. */
    record Created(WebhookSubscription subscription, String secret) {
    }
}
