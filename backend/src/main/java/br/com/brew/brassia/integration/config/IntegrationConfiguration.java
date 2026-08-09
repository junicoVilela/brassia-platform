package br.com.brew.brassia.integration.config;

import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.integration.application.port.inbound.SubscriptionCommands;
import br.com.brew.brassia.integration.application.port.inbound.SubscriptionQueries;
import br.com.brew.brassia.integration.application.port.outbound.WebhookDeliveryRepository;
import br.com.brew.brassia.integration.application.port.outbound.WebhookSender;
import br.com.brew.brassia.integration.application.port.outbound.WebhookSubscriptionRepository;
import br.com.brew.brassia.integration.application.service.DeliveryDispatcher;
import br.com.brew.brassia.integration.application.service.EventEnqueuer;
import br.com.brew.brassia.integration.application.service.SubscriptionHandlers;
import java.time.Clock;
import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Composição das integrações (INT-002).
 *
 * <p><strong>O {@link EventEnqueuer} é exposto sem transação própria, e isso é deliberado.</strong> Ele é
 * chamado de dentro do listener que roda em {@code BEFORE_COMMIT} da transação do comando — abrir uma
 * transação nova ali quebraria justamente a propriedade que faz o outbox valer: a entrega precisa
 * reverter junto com o fato que a originou.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class IntegrationConfiguration {

    @Bean
    SubscriptionCommands webhookSubscriptionCommands(WebhookSubscriptionRepository subscriptions,
            AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new SubscriptionHandlers.Commands(subscriptions, audit, Clock.systemUTC());
        var transaction = new TransactionTemplate(transactionManager);
        return new SubscriptionCommands() {
            @Override
            public Created create(CreateRequest request) {
                return Objects.requireNonNull(transaction.execute(s -> handler.create(request)));
            }

            @Override
            public br.com.brew.brassia.integration.domain.WebhookSubscription changeStatus(
                    ChangeStatusRequest request) {
                return Objects.requireNonNull(transaction.execute(s -> handler.changeStatus(request)));
            }
        };
    }

    @Bean
    SubscriptionQueries webhookSubscriptionQueries(WebhookSubscriptionRepository subscriptions,
            WebhookDeliveryRepository deliveries) {
        return new SubscriptionHandlers.Queries(subscriptions, deliveries);
    }

    @Bean
    EventEnqueuer webhookEventEnqueuer(WebhookSubscriptionRepository subscriptions,
            WebhookDeliveryRepository deliveries) {
        return new EventEnqueuer(subscriptions, deliveries, Clock.systemUTC());
    }

    @Bean
    DeliveryDispatcher webhookDeliveryDispatcher(WebhookDeliveryRepository deliveries,
            WebhookSubscriptionRepository subscriptions, WebhookSender sender, AuditTrail audit) {
        return new DeliveryDispatcher(deliveries, subscriptions, sender, audit, Clock.systemUTC());
    }
}
