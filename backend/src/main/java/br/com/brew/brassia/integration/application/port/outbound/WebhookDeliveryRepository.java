package br.com.brew.brassia.integration.application.port.outbound;

import br.com.brew.brassia.integration.domain.WebhookDelivery;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** O outbox das entregas (INT-002). */
public interface WebhookDeliveryRepository {

    /**
     * Enfileira, ignorando o que já está enfileirado para o mesmo evento e a mesma assinatura.
     *
     * @return {@code true} quando entrou; {@code false} quando já existia.
     */
    boolean enqueueIfAbsent(WebhookDelivery delivery);

    void update(WebhookDelivery delivery);

    /**
     * Entregas prontas para tentar, travadas para este processo.
     *
     * <p>O travamento é o que permite mais de uma instância da aplicação: sem ele, duas instâncias
     * pegariam a mesma entrega e o destino receberia o evento em duplicidade — que é exatamente o que a
     * outra ponta não consegue distinguir de dois fatos iguais.
     */
    List<WebhookDelivery> claimDue(Instant now, int limit);

    List<WebhookDelivery> recentOf(UUID breweryId, UUID subscriptionId, int limit);
}
