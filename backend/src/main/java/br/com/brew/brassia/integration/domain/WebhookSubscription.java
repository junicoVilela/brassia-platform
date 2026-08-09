package br.com.brew.brassia.integration.domain;

import java.net.URI;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Uma assinatura de webhook: para onde ir e o que mandar (INT-002).
 *
 * <p>O segredo mora aqui porque é dele que sai a assinatura de cada entrega, mas <strong>nunca sai por
 * leitura</strong>: não há acessor que o devolva a um DTO, e a única coisa que o mundo vê é o prefixo.
 * A regra do AGENTS.md — nunca registrar tokens em log — só vale se o valor não tiver por onde escapar.
 */
public final class WebhookSubscription {

    private final UUID id;
    private final UUID breweryId;
    private final String name;
    private final URI endpoint;
    private final String secret;
    private final Set<WebhookEventType> events;
    private final SubscriptionStatus status;
    private final UUID createdBy;
    private final Instant createdAt;
    private final long version;

    private WebhookSubscription(UUID id, UUID breweryId, String name, URI endpoint, String secret,
            Set<WebhookEventType> events, SubscriptionStatus status, UUID createdBy, Instant createdAt,
            long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.name = Objects.requireNonNull(name, "name");
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.secret = Objects.requireNonNull(secret, "secret");
        this.events = events.isEmpty() ? Set.of() : EnumSet.copyOf(events);
        this.status = Objects.requireNonNull(status, "status");
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.version = version;
    }

    public static WebhookSubscription create(UUID breweryId, String name, String rawEndpoint,
            String secret, Set<WebhookEventType> events, UUID actorId, Instant now) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("nome da assinatura é obrigatório");
        }
        if (events == null || events.isEmpty()) {
            // Assinatura sem evento nenhum não é configuração parcial: é uma linha que nunca dispara e
            // que quem a criou acredita estar funcionando.
            throw new IllegalArgumentException("selecione ao menos um tipo de evento");
        }
        if (secret == null || secret.length() < 32) {
            // 32 caracteres não é burocracia: um segredo curto torna a assinatura falsificável por força
            // bruta offline, e aí ela deixa de provar qualquer coisa.
            throw new IllegalArgumentException("o segredo deve ter ao menos 32 caracteres");
        }
        return new WebhookSubscription(UUID.randomUUID(), breweryId, name.trim(), requireHttps(rawEndpoint),
                secret, events, SubscriptionStatus.ACTIVE, actorId, now, 0L);
    }

    /**
     * Só HTTPS, e não é preferência.
     *
     * <p>A assinatura protege a <em>integridade</em> do corpo, não o sigilo dele. Um webhook em HTTP puro
     * entrega em texto claro o que aconteceu na cervejaria — receita publicada, lote liberado, ciclo de
     * limpeza — para qualquer um no caminho. E aceitar `http://` "só para teste" é exatamente como uma
     * URL de teste vai para produção.
     */
    private static URI requireHttps(String rawEndpoint) {
        if (rawEndpoint == null || rawEndpoint.isBlank()) {
            throw new IllegalArgumentException("endereço de destino é obrigatório");
        }
        URI uri;
        try {
            uri = URI.create(rawEndpoint.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("endereço de destino inválido");
        }
        var scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme)) {
            throw new IllegalArgumentException("o destino precisa ser https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("endereço de destino sem host");
        }
        return uri;
    }

    public static WebhookSubscription reconstitute(UUID id, UUID breweryId, String name, URI endpoint,
            String secret, Set<WebhookEventType> events, SubscriptionStatus status, UUID createdBy,
            Instant createdAt, long version) {
        return new WebhookSubscription(id, breweryId, name, endpoint, secret, events, status, createdBy,
                createdAt, version);
    }

    public WebhookSubscription changeStatusTo(SubscriptionStatus target) {
        Objects.requireNonNull(target, "estado é obrigatório");
        if (status == target) {
            throw new IllegalStateException("assinatura já está em " + target);
        }
        return new WebhookSubscription(id, breweryId, name, endpoint, secret, events, target, createdBy,
                createdAt, version);
    }

    public boolean subscribesTo(WebhookEventType type) {
        return status == SubscriptionStatus.ACTIVE && events.contains(type);
    }

    /**
     * Assina um corpo com o segredo desta assinatura.
     *
     * <p>A assinatura é calculada <strong>aqui dentro</strong> justamente para que o segredo não precise
     * sair do agregado no caminho normal. O despachante pede uma assinatura, não uma chave.
     */
    public String sign(long epochSeconds, String payload) {
        return WebhookSignature.sign(secret, epochSeconds, payload);
    }

    /**
     * O segredo em claro, <strong>exclusivamente para gravar</strong>.
     *
     * <p>O nome é longo e desconfortável de propósito: ele existe porque a persistência precisa do valor,
     * e um {@code secret()} curto seria chamado por engano na montagem de um DTO — que é exatamente como
     * um segredo vaza para uma resposta HTTP. Quem precisa mostrá-lo a um humano usa
     * {@link #maskedSecret()}; quem precisa assiná-lo usa {@link #sign}.
     */
    public String secretForPersistence() {
        return secret;
    }

    /**
     * Como o segredo aparece para quem administra: só os primeiros caracteres.
     *
     * <p>Serve para conferir "é este mesmo o segredo que configurei do outro lado?" sem revelar o valor.
     * Mostrar nada tornaria impossível distinguir duas assinaturas mal configuradas.
     */
    public String maskedSecret() {
        return secret.substring(0, 4) + "…" + "*".repeat(8);
    }

    public UUID id() { return id; }
    public UUID breweryId() { return breweryId; }
    public String name() { return name; }
    public URI endpoint() { return endpoint; }
    public Set<WebhookEventType> events() { return events; }
    public SubscriptionStatus status() { return status; }
    public UUID createdBy() { return createdBy; }
    public Instant createdAt() { return createdAt; }
    public long version() { return version; }
}
