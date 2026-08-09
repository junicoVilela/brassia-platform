package br.com.brew.brassia.integration.adapter.inbound.web.dto;

import br.com.brew.brassia.integration.application.port.inbound.SubscriptionCommands;
import br.com.brew.brassia.integration.domain.WebhookDelivery;
import br.com.brew.brassia.integration.domain.WebhookEventType;
import br.com.brew.brassia.integration.domain.WebhookSubscription;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Contratos HTTP dos webhooks (INT-002). */
public final class WebhookDtos {

    private WebhookDtos() {
    }

    public record CreateRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 500) String endpoint,
            @NotEmpty Set<String> events) {
    }

    public record ChangeStatusRequest(
            @NotBlank String status,
            @NotNull Long expectedVersion) {
    }

    /**
     * A assinatura como o mundo a vê.
     *
     * <p>Não há campo de segredo. {@code secretHint} são os primeiros caracteres, o suficiente para
     * conferir "é este mesmo o que configurei do outro lado?" sem revelar o valor — mostrar nada tornaria
     * impossível distinguir duas assinaturas mal configuradas.
     */
    public record SubscriptionView(
            UUID id,
            String name,
            String endpoint,
            List<String> events,
            String status,
            String secretHint,
            Instant createdAt,
            long version) {

        public static SubscriptionView from(WebhookSubscription subscription) {
            return new SubscriptionView(subscription.id(), subscription.name(),
                    subscription.endpoint().toString(),
                    subscription.events().stream().map(WebhookEventType::externalName).toList(),
                    subscription.status().name(), subscription.maskedSecret(), subscription.createdAt(),
                    subscription.version());
        }

        public static List<SubscriptionView> from(List<WebhookSubscription> subscriptions) {
            return subscriptions.stream().map(SubscriptionView::from).toList();
        }
    }

    /**
     * A resposta da criação — a <strong>única</strong> vez em que o segredo aparece.
     *
     * <p>Mesmo raciocínio de uma API key: ele precisa chegar a quem vai configurar o outro lado, e depois
     * disso não há motivo legítimo para lê-lo de volta. Manter um caminho de leitura seria manter uma
     * porta que só serve para vazar.
     */
    public record CreatedView(SubscriptionView subscription, String secret, String warning) {

        public static CreatedView from(SubscriptionCommands.Created created) {
            return new CreatedView(SubscriptionView.from(created.subscription()), created.secret(),
                    "Guarde este segredo agora: ele não será exibido novamente.");
        }
    }

    public record DeliveryView(
            UUID id,
            String eventType,
            String eventId,
            String status,
            int attempts,
            Instant nextAttemptAt,
            Instant deliveredAt,
            Integer lastResponseStatus,
            String lastError,
            Instant createdAt) {

        public static DeliveryView from(WebhookDelivery delivery) {
            return new DeliveryView(delivery.id(), delivery.eventType().externalName(), delivery.eventId(),
                    delivery.status().name(), delivery.attempts(), delivery.nextAttemptAt(),
                    delivery.deliveredAt(), delivery.lastResponseStatus(), delivery.lastError(),
                    delivery.createdAt());
        }

        public static List<DeliveryView> from(List<WebhookDelivery> deliveries) {
            return deliveries.stream().map(DeliveryView::from).toList();
        }
    }
}
