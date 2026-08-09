package br.com.brew.brassia.integration.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.integration.application.port.inbound.SubscriptionCommands;
import br.com.brew.brassia.integration.application.port.inbound.SubscriptionQueries;
import br.com.brew.brassia.integration.application.port.outbound.WebhookDeliveryRepository;
import br.com.brew.brassia.integration.application.port.outbound.WebhookSubscriptionRepository;
import br.com.brew.brassia.integration.domain.UnknownSubscriptionException;
import br.com.brew.brassia.integration.domain.WebhookDelivery;
import br.com.brew.brassia.integration.domain.WebhookEventType;
import br.com.brew.brassia.integration.domain.WebhookSubscription;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/** Casos de uso das assinaturas de webhook (INT-002). */
public final class SubscriptionHandlers {

    private SubscriptionHandlers() {
    }

    /** Criar e mudar o estado de uma assinatura. */
    public static final class Commands implements SubscriptionCommands {

        /**
         * 32 bytes de aleatoriedade criptográfica, em base64 sem padding.
         *
         * <p><strong>O segredo é gerado aqui, não recebido de quem cadastra.</strong> Aceitá-lo do cliente
         * significaria aceitar "senha123" com aparência de configuração legítima, e a assinatura HMAC só
         * prova alguma coisa enquanto o segredo é imprevisível. É o mesmo motivo pelo qual não se deixa o
         * usuário escolher o próprio token de API.
         */
        private static final int SECRET_BYTES = 32;

        private final WebhookSubscriptionRepository subscriptions;
        private final AuditTrail audit;
        private final SecureRandom random;
        private final Clock clock;

        public Commands(WebhookSubscriptionRepository subscriptions, AuditTrail audit, Clock clock) {
            this.subscriptions = Objects.requireNonNull(subscriptions, "subscriptions");
            this.audit = Objects.requireNonNull(audit, "audit");
            this.clock = Objects.requireNonNull(clock, "clock");
            this.random = new SecureRandom();
        }

        @Override
        public Created create(CreateRequest request) {
            Objects.requireNonNull(request, "request");

            var events = request.events() == null ? java.util.Set.<WebhookEventType>of()
                    : request.events().stream().map(WebhookEventType::of)
                            .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

            var secret = generateSecret();
            var subscription = WebhookSubscription.create(request.breweryId(), request.name(),
                    request.endpoint(), secret, events, request.actorId(), clock.instant());

            subscriptions.insert(subscription);

            var metadata = new LinkedHashMap<String, String>();
            metadata.put("name", subscription.name());
            // O HOST, não a URL inteira: o caminho de um webhook às vezes carrega token, e a trilha de
            // auditoria não é lugar para ele. O segredo, evidentemente, não entra de forma alguma.
            metadata.put("host", subscription.endpoint().getHost());
            metadata.put("events", subscription.events().stream().map(WebhookEventType::externalName)
                    .collect(Collectors.joining(",")));
            audit.record(AuditEvent.success(subscription.breweryId(), request.actorId(),
                    "integration.webhook.create", "webhook_subscription", subscription.id().toString(),
                    metadata));

            return new Created(subscription, secret);
        }

        private String generateSecret() {
            var bytes = new byte[SECRET_BYTES];
            random.nextBytes(bytes);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        }

        @Override
        public WebhookSubscription changeStatus(ChangeStatusRequest request) {
            Objects.requireNonNull(request, "request");

            var current = subscriptions.byId(request.breweryId(), request.subscriptionId())
                    .orElseThrow(() -> new UnknownSubscriptionException(request.subscriptionId()));

            var changed = current.changeStatusTo(request.target());
            if (!subscriptions.updateStatus(changed, request.expectedVersion())) {
                throw new IllegalStateException("assinatura alterada por outra operação");
            }

            var metadata = new LinkedHashMap<String, String>();
            metadata.put("name", current.name());
            metadata.put("host", current.endpoint().getHost());
            metadata.put("from", current.status().name());
            metadata.put("to", changed.status().name());
            audit.record(AuditEvent.success(changed.breweryId(), request.actorId(),
                    "integration.webhook.status", "webhook_subscription", changed.id().toString(),
                    metadata));
            return changed;
        }
    }

    /** Consultas. */
    public static final class Queries implements SubscriptionQueries {

        private static final int MAX_LIMIT = 200;

        private final WebhookSubscriptionRepository subscriptions;
        private final WebhookDeliveryRepository deliveries;

        public Queries(WebhookSubscriptionRepository subscriptions,
                WebhookDeliveryRepository deliveries) {
            this.subscriptions = Objects.requireNonNull(subscriptions, "subscriptions");
            this.deliveries = Objects.requireNonNull(deliveries, "deliveries");
        }

        @Override
        public List<WebhookSubscription> subscriptions(UUID breweryId) {
            return subscriptions.findAll(Objects.requireNonNull(breweryId, "breweryId"));
        }

        @Override
        public List<WebhookDelivery> deliveries(UUID breweryId, UUID subscriptionId, int limit) {
            Objects.requireNonNull(breweryId, "breweryId");
            Objects.requireNonNull(subscriptionId, "subscriptionId");
            // A assinatura é resolvida dentro da cervejaria antes das entregas: sem isso, um id de outra
            // cervejaria devolveria lista vazia — que é a resposta de "não há entregas", não a de "esta
            // assinatura não é sua".
            subscriptions.byId(breweryId, subscriptionId)
                    .orElseThrow(() -> new UnknownSubscriptionException(subscriptionId));
            return deliveries.recentOf(breweryId, subscriptionId, Math.min(Math.max(limit, 1), MAX_LIMIT));
        }
    }
}
