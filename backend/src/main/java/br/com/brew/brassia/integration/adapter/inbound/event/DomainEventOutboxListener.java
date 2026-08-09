package br.com.brew.brassia.integration.adapter.inbound.event;

import br.com.brew.brassia.integration.application.service.EventEnqueuer;
import br.com.brew.brassia.integration.domain.WebhookEventType;
import br.com.brew.brassia.planning.BrewOrderCancelled;
import br.com.brew.brassia.planning.BrewOrderReleased;
import br.com.brew.brassia.planning.BrewOrderStarted;
import br.com.brew.brassia.recipe.RecipePublished;
import br.com.brew.brassia.sanitation.CleaningCycleReleased;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Traduz eventos de domínio em entregas no outbox (INT-002).
 *
 * <p><strong>{@code BEFORE_COMMIT} é a decisão inteira desta classe.</strong> A entrega é gravada
 * <em>dentro</em> da transação do comando que originou o evento: se a liberação da OP reverter, a entrega
 * reverte junto, e o webhook "ordem liberada" não sai para uma ordem que não existe. Um webhook não se
 * desmanda.
 *
 * <p>{@code AFTER_COMMIT} pareceria mais seguro e seria pior de duas formas: a gravação aconteceria fora
 * da transação, criando a janela em que o fato está commitado e a intenção de entregar não — evento
 * perdido se o processo cair no meio —, e um erro aqui deixaria de ser reversível junto com o comando.
 *
 * <p>O que <strong>não</strong> acontece aqui é o envio. Enfileirar é barato e local; entregar depende de
 * um servidor de terceiro e roda depois, noutro processo. É essa separação que faz "falha não bloqueia
 * domínio" ser estrutural.
 *
 * <p>O payload é montado <strong>a partir do evento</strong>, não relido do banco. O evento é o fato como
 * ele foi naquele instante; reler entregaria o estado de agora sob o nome de um fato de antes.
 */
@Component
class DomainEventOutboxListener {

    /**
     * Mapper próprio, como no resto do projeto.
     *
     * <p>O corpo do webhook é contrato com terceiros e não pode herdar a configuração de serialização da
     * API HTTP: mudar um formato de data para a tela passaria a mudar o payload de quem integra, sem que
     * ninguém tivesse decidido isso.
     */
    private static final ObjectMapper JSON = new ObjectMapper();

    private final EventEnqueuer enqueuer;

    DomainEventOutboxListener(EventEnqueuer enqueuer) {
        this.enqueuer = Objects.requireNonNull(enqueuer, "enqueuer");
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    void on(BrewOrderReleased event) {
        var body = base(event.occurredAt());
        body.put("orderId", event.orderId().toString());
        body.put("code", event.code());
        body.put("recipeId", event.recipeId().toString());
        enqueue(event.breweryId(), WebhookEventType.BREW_ORDER_RELEASED, event.orderId(), body);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    void on(BrewOrderStarted event) {
        var body = base(event.occurredAt());
        body.put("orderId", event.orderId().toString());
        body.put("code", event.code());
        body.put("recipeId", event.recipeId().toString());
        body.put("recipeVersion", event.recipeVersion());
        body.put("recipeName", event.recipeName());
        body.put("volumeLiters", event.volumeLiters());
        enqueue(event.breweryId(), WebhookEventType.BREW_ORDER_STARTED, event.orderId(), body);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    void on(BrewOrderCancelled event) {
        var body = base(event.occurredAt());
        body.put("orderId", event.orderId().toString());
        body.put("code", event.code());
        // O motivo do cancelamento é texto escrito por uma pessoa; vai como veio, e é por isso que o
        // corpo é montado com Jackson e não concatenado: aspas e quebras de linha precisam ser escapadas.
        body.put("reason", event.reason());
        enqueue(event.breweryId(), WebhookEventType.BREW_ORDER_CANCELLED, event.orderId(), body);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    void on(RecipePublished event) {
        var body = base(event.occurredAt());
        body.put("recipeId", event.recipeId().toString());
        body.put("version", event.version());
        // A identidade do fato inclui a versão: publicar a v2 da mesma receita é outro fato, e sem isso a
        // restrição única do outbox trataria a segunda publicação como repetição da primeira.
        enqueue(event.breweryId(), WebhookEventType.RECIPE_PUBLISHED,
                event.recipeId() + ":" + event.version(), body);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    void on(CleaningCycleReleased event) {
        var body = base(event.releasedAt());
        body.put("cycleId", event.cycleId().toString());
        body.put("equipmentId", event.equipmentId().toString());
        body.put("procedureCode", event.procedureCode());
        body.put("procedureVersion", event.procedureVersion());
        enqueue(event.breweryId(), WebhookEventType.CLEANING_CYCLE_RELEASED, event.cycleId(), body);
    }

    private ObjectNode base(java.time.Instant occurredAt) {
        var node = JSON.createObjectNode();
        node.put("occurredAt", occurredAt.toString());
        return node;
    }

    private void enqueue(UUID breweryId, WebhookEventType type, UUID eventId, ObjectNode body) {
        enqueue(breweryId, type, eventId.toString(), body);
    }

    private void enqueue(UUID breweryId, WebhookEventType type, String eventId, ObjectNode body) {
        body.put("event", type.externalName());
        body.put("breweryId", breweryId.toString());
        enqueuer.enqueue(breweryId, type, eventId, body.toString());
    }
}
