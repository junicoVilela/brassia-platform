package br.com.brew.brassia.integration.application;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.integration.application.port.outbound.WebhookDeliveryRepository;
import br.com.brew.brassia.integration.application.port.outbound.WebhookSender;
import br.com.brew.brassia.integration.application.port.outbound.WebhookSubscriptionRepository;
import br.com.brew.brassia.integration.application.service.DeliveryDispatcher;
import br.com.brew.brassia.integration.domain.DeliveryStatus;
import br.com.brew.brassia.integration.domain.SubscriptionStatus;
import br.com.brew.brassia.integration.domain.WebhookDelivery;
import br.com.brew.brassia.integration.domain.WebhookEventType;
import br.com.brew.brassia.integration.domain.WebhookSignature;
import br.com.brew.brassia.integration.domain.WebhookSubscription;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O despachante e o retry (INT-002).
 *
 * <p>O dublê do {@link WebhookSender} existe para exercitar o que não se consegue provocar de forma
 * confiável contra um servidor real: timeout, 500 e recuperação depois de N falhas — que são exatamente o
 * comportamento que a história pede.
 */
class DeliveryDispatcherTest {

    private static final UUID CERVEJARIA = UUID.randomUUID();
    private static final UUID OPERADOR = UUID.randomUUID();
    private static final String SEGREDO = "um-segredo-com-mais-de-32-caracteres-ok";
    private static final Instant AGORA = Instant.parse("2026-08-09T10:00:00Z");

    private FakeSubscriptions subscriptions;
    private FakeDeliveries deliveries;
    private FakeSender sender;
    private RecordingAudit audit;
    private WebhookSubscription assinatura;

    @BeforeEach
    void setUp() {
        subscriptions = new FakeSubscriptions();
        deliveries = new FakeDeliveries();
        sender = new FakeSender();
        audit = new RecordingAudit();
        assinatura = WebhookSubscription.create(CERVEJARIA, "ERP", "https://erp.example.com/hooks",
                SEGREDO, Set.of(WebhookEventType.BREW_ORDER_RELEASED), OPERADOR, AGORA);
        subscriptions.save(assinatura);
    }

    private DeliveryDispatcher dispatcherAt(Instant now) {
        return new DeliveryDispatcher(deliveries, subscriptions, sender, audit,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private WebhookDelivery enfileirar() {
        var delivery = WebhookDelivery.enqueue(CERVEJARIA, assinatura.id(),
                WebhookEventType.BREW_ORDER_RELEASED, "order-1", "{\"a\":1}", AGORA);
        deliveries.enqueueIfAbsent(delivery);
        return delivery;
    }

    @Test
    @DisplayName("entrega bem-sucedida vira DELIVERED e sai da fila")
    void entregaComSucesso() {
        enfileirar();
        sender.respondWith(WebhookSender.Result.ok(200));

        assertThat(dispatcherAt(AGORA).dispatchDue()).isEqualTo(1);

        assertThat(deliveries.only().status()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(deliveries.only().attempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("a requisição vai assinada, e a assinatura confere com o segredo")
    void requisicaoVaiAssinada() {
        enfileirar();
        sender.respondWith(WebhookSender.Result.ok(200));

        dispatcherAt(AGORA).dispatchDue();

        var headers = sender.lastHeaders;
        var timestamp = Long.parseLong(headers.get("X-Brassia-Timestamp"));
        var esperada = WebhookSignature.sign(SEGREDO, timestamp, sender.lastBody);

        assertThat(WebhookSignature.matches(esperada, headers.get("X-Brassia-Signature"))).isTrue();
        assertThat(headers.get("X-Brassia-Event")).isEqualTo("brew_order.released");
        assertThat(headers.get("X-Brassia-Event-Id")).isEqualTo("order-1");
    }

    @Test
    @DisplayName("o segredo NÃO viaja em cabeçalho nenhum")
    void segredoNaoViaja() {
        enfileirar();
        sender.respondWith(WebhookSender.Result.ok(200));

        dispatcherAt(AGORA).dispatchDue();

        assertThat(sender.lastHeaders.values()).noneMatch(v -> v.contains(SEGREDO));
        assertThat(sender.lastBody).doesNotContain(SEGREDO);
    }

    @Test
    @DisplayName("falha do destino não estoura: a entrega volta para a fila com backoff")
    void falhaNaoEstoura() {
        // É o critério "falha não bloqueia domínio" visto daqui: nada sobe, nada interrompe.
        enfileirar();
        sender.respondWith(WebhookSender.Result.rejected(500, "erro"));

        assertThat(dispatcherAt(AGORA).dispatchDue()).isEqualTo(1);

        assertThat(deliveries.only().status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(deliveries.only().attempts()).isEqualTo(1);
        assertThat(deliveries.only().nextAttemptAt()).isAfter(AGORA);
    }

    @Test
    @DisplayName("destino inalcançável é registrado sem status: 'não respondeu' ≠ 'respondeu 500'")
    void inalcancavelNaoTemStatus() {
        enfileirar();
        sender.respondWith(WebhookSender.Result.unreachable("HttpConnectTimeoutException"));

        dispatcherAt(AGORA).dispatchDue();

        assertThat(deliveries.only().lastResponseStatus()).isNull();
        assertThat(deliveries.only().lastError()).isEqualTo("HttpConnectTimeoutException");
    }

    @Test
    @DisplayName("depois de cinco falhas a entrega esgota e é auditada com o destino e as tentativas")
    void esgotaEAudita() {
        enfileirar();
        sender.respondWith(WebhookSender.Result.rejected(500, "erro"));

        var agora = AGORA;
        for (int i = 0; i < WebhookDelivery.MAX_ATTEMPTS; i++) {
            dispatcherAt(agora).dispatchDue();
            var pendente = deliveries.only();
            agora = pendente.nextAttemptAt() == null ? agora : pendente.nextAttemptAt();
        }

        assertThat(deliveries.only().status()).isEqualTo(DeliveryStatus.EXHAUSTED);
        var evento = audit.events.stream()
                .filter(e -> e.action().equals("integration.webhook.delivery")).findFirst().orElseThrow();
        assertThat(evento.metadata()).containsEntry("outcome", "EXHAUSTED");
        assertThat(evento.metadata()).containsEntry("attempts", "5");
        assertThat(evento.metadata()).containsEntry("host", "erp.example.com");
    }

    @Test
    @DisplayName("a auditoria guarda o HOST, nunca a URL completa nem o segredo")
    void auditoriaNaoVazaCaminhoNemSegredo() {
        // O caminho de um webhook às vezes carrega token, e a trilha de auditoria não é lugar para ele.
        enfileirar();
        sender.respondWith(WebhookSender.Result.ok(200));

        dispatcherAt(AGORA).dispatchDue();

        var evento = audit.events.getFirst();
        assertThat(evento.metadata()).containsEntry("host", "erp.example.com");
        assertThat(evento.metadata().values()).noneMatch(v -> v.contains("/hooks"));
        assertThat(evento.metadata().values()).noneMatch(v -> v.contains(SEGREDO));
    }

    @Test
    @DisplayName("tentativa intermediária não é auditada: retry é esperado, não fato a guardar")
    void tentativaIntermediariaNaoEAuditada() {
        enfileirar();
        sender.respondWith(WebhookSender.Result.rejected(503, "indisponível"));

        dispatcherAt(AGORA).dispatchDue();

        assertThat(audit.events).isEmpty();
    }

    @Test
    @DisplayName("assinatura revogada no meio do caminho desiste de uma vez, sem gastar o backoff")
    void revogadaDesisteDeUmaVez() {
        enfileirar();
        subscriptions.save(assinatura.changeStatusTo(SubscriptionStatus.REVOKED));

        dispatcherAt(AGORA).dispatchDue();

        assertThat(deliveries.only().status()).isEqualTo(DeliveryStatus.EXHAUSTED);
        assertThat(deliveries.only().lastError()).isEqualTo("assinatura revogada");
        // E não chegou a tentar a rede.
        assertThat(sender.calls).isZero();
    }

    @Test
    @DisplayName("uma entrega que falha não impede as outras da mesma rodada")
    void falhaDeUmaNaoParaAsOutras() {
        // Um destino mal configurado de uma cervejaria não pode impedir as demais de receberem as delas.
        var outra = WebhookSubscription.create(CERVEJARIA, "BI", "https://bi.example.com/hooks", SEGREDO,
                Set.of(WebhookEventType.BREW_ORDER_RELEASED), OPERADOR, AGORA);
        subscriptions.save(outra);
        enfileirar();
        deliveries.enqueueIfAbsent(WebhookDelivery.enqueue(CERVEJARIA, outra.id(),
                WebhookEventType.BREW_ORDER_RELEASED, "order-1", "{\"a\":1}", AGORA));

        sender.explodeOnFirstCall();

        assertThat(dispatcherAt(AGORA).dispatchDue()).isEqualTo(2);
        assertThat(deliveries.all().stream().filter(d -> d.status() == DeliveryStatus.DELIVERED).count())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("entrega ainda em backoff não é tentada")
    void backoffRespeitado() {
        enfileirar();
        sender.respondWith(WebhookSender.Result.rejected(500, "erro"));
        dispatcherAt(AGORA).dispatchDue();

        assertThat(dispatcherAt(AGORA.plusSeconds(5)).dispatchDue()).isZero();
    }

    // --- dublês ---

    private static final class FakeSubscriptions implements WebhookSubscriptionRepository {
        private final java.util.Map<UUID, WebhookSubscription> byId = new java.util.HashMap<>();

        void save(WebhookSubscription s) {
            byId.put(s.id(), s);
        }

        @Override public void insert(WebhookSubscription s) { save(s); }

        @Override public boolean updateStatus(WebhookSubscription s, long expectedVersion) {
            save(s);
            return true;
        }

        @Override public Optional<WebhookSubscription> byId(UUID breweryId, UUID id) {
            return Optional.ofNullable(byId.get(id)).filter(s -> s.breweryId().equals(breweryId));
        }

        @Override public List<WebhookSubscription> findAll(UUID breweryId) {
            return byId.values().stream().filter(s -> s.breweryId().equals(breweryId)).toList();
        }

        @Override public List<WebhookSubscription> activeFor(UUID breweryId, WebhookEventType type) {
            return byId.values().stream()
                    .filter(s -> s.breweryId().equals(breweryId) && s.subscribesTo(type)).toList();
        }
    }

    private static final class FakeDeliveries implements WebhookDeliveryRepository {
        private final List<WebhookDelivery> stored = new ArrayList<>();

        @Override public boolean enqueueIfAbsent(WebhookDelivery delivery) {
            var exists = stored.stream().anyMatch(d -> d.subscriptionId().equals(delivery.subscriptionId())
                    && d.eventId().equals(delivery.eventId()));
            if (exists) {
                return false;
            }
            stored.add(delivery);
            return true;
        }

        @Override public void update(WebhookDelivery delivery) {
            stored.replaceAll(d -> d.id().equals(delivery.id()) ? delivery : d);
        }

        @Override public List<WebhookDelivery> claimDue(Instant now, int limit) {
            return stored.stream().filter(d -> d.isDue(now)).limit(limit).toList();
        }

        @Override public List<WebhookDelivery> recentOf(UUID breweryId, UUID subscriptionId, int limit) {
            return stored.stream().filter(d -> d.subscriptionId().equals(subscriptionId)).toList();
        }

        WebhookDelivery only() {
            return stored.getFirst();
        }

        List<WebhookDelivery> all() {
            return stored;
        }
    }

    private static final class FakeSender implements WebhookSender {
        private Result next = Result.ok(200);
        private boolean explodeFirst;
        int calls;
        Map<String, String> lastHeaders = Map.of();
        String lastBody = "";

        void respondWith(Result result) {
            this.next = result;
        }

        void explodeOnFirstCall() {
            this.explodeFirst = true;
        }

        @Override public Result send(URI endpoint, Map<String, String> headers, String body) {
            calls++;
            lastHeaders = headers;
            lastBody = body;
            if (explodeFirst) {
                explodeFirst = false;
                throw new IllegalStateException("defeito nosso ao montar a entrega");
            }
            return next;
        }
    }

    private static final class RecordingAudit implements AuditTrail {
        private final List<AuditEvent> events = new ArrayList<>();

        @Override public void record(AuditEvent event) {
            events.add(event);
        }
    }
}
