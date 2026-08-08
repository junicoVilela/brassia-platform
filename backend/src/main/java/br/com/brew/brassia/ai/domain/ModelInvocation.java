package br.com.brew.brassia.ai.domain;

import br.com.brew.brassia.ai.ModelPurpose;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * O registro de uma chamada ao modelo: quem pediu, para quê, em qual modelo, quanto custou e como
 * terminou (AIA-001).
 *
 * <p><strong>Toda chamada vira linha, inclusive as que falharam.</strong> É o ponto do desenho: o
 * custo que ninguém registrou é o custo que ninguém previu, e uma resposta recusada por contrato foi
 * paga do mesmo jeito — o provedor cobrou pelos tokens que gerou, o contrato não valeu, e é
 * justamente essa combinação que precisa aparecer numa conta. Registrar só sucesso daria um relatório
 * que subestima o gasto e esconde o prompt que está errado.
 *
 * <p>Linha imutável, como medição e movimento de estoque: não se corrige uma chamada que já
 * aconteceu.
 *
 * <p><strong>O que não entra aqui.</strong> Nem prompt, nem resposta, nem trecho de documento. O
 * conteúdo é a parte sensível — POP, laudo, dado de cliente — e este registro existe para explicar
 * custo e disponibilidade, não para guardar texto.
 */
public final class ModelInvocation {

    private final UUID id;
    private final UUID breweryId;
    private final UUID actorId;
    private final ModelPurpose purpose;
    private final String provider;
    private final String model;
    private final InvocationStatus status;
    private final TokenUsage usage;
    private final BigDecimal cost;
    private final String currency;
    private final long latencyMillis;
    private final String failureReason;
    private final Instant occurredAt;

    private ModelInvocation(UUID id, UUID breweryId, UUID actorId, ModelPurpose purpose, String provider,
            String model, InvocationStatus status, TokenUsage usage, BigDecimal cost, String currency,
            long latencyMillis, String failureReason, Instant occurredAt) {
        this.id = Objects.requireNonNull(id);
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.actorId = Objects.requireNonNull(actorId, "actorId é obrigatório: a IA não age sem autor");
        this.purpose = Objects.requireNonNull(purpose, "purpose");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.model = Objects.requireNonNull(model, "model");
        this.status = Objects.requireNonNull(status, "status");
        this.usage = Objects.requireNonNull(usage, "usage");
        this.cost = Objects.requireNonNull(cost, "cost");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.latencyMillis = latencyMillis;
        this.failureReason = failureReason;
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    /** Chamada que respondeu dentro do contrato. */
    public static ModelInvocation succeeded(UUID breweryId, UUID actorId, ModelPurpose purpose,
            String provider, String model, TokenUsage usage, BigDecimal cost, String currency,
            long latencyMillis, Instant at) {
        return new ModelInvocation(UUID.randomUUID(), breweryId, actorId, purpose, provider, model,
                InvocationStatus.SUCCEEDED, usage, cost, currency, latencyMillis, null, at);
    }

    /**
     * Chamada que terminou mal, com o motivo por extenso.
     *
     * <p>Tokens e custo vêm mesmo assim quando o provedor gerou algo: uma resposta recusada por
     * contrato custou dinheiro. Quando não houve chamada — provedor desligado, orçamento estourado —
     * vêm zerados, porque não houve gasto.
     */
    public static ModelInvocation failed(UUID breweryId, UUID actorId, ModelPurpose purpose,
            String provider, String model, InvocationStatus status, TokenUsage usage, BigDecimal cost,
            String currency, long latencyMillis, String failureReason, Instant at) {
        if (status == InvocationStatus.SUCCEEDED) {
            throw new IllegalArgumentException("use succeeded(...) para chamada bem-sucedida");
        }
        return new ModelInvocation(UUID.randomUUID(), breweryId, actorId, purpose, provider, model,
                status, usage, cost, currency, latencyMillis,
                Objects.requireNonNull(failureReason, "motivo da falha é obrigatório"), at);
    }

    public static ModelInvocation reconstitute(UUID id, UUID breweryId, UUID actorId, ModelPurpose purpose,
            String provider, String model, InvocationStatus status, TokenUsage usage, BigDecimal cost,
            String currency, long latencyMillis, String failureReason, Instant occurredAt) {
        return new ModelInvocation(id, breweryId, actorId, purpose, provider, model, status, usage, cost,
                currency, latencyMillis, failureReason, occurredAt);
    }

    public UUID id() { return id; }
    public UUID breweryId() { return breweryId; }
    public UUID actorId() { return actorId; }
    public ModelPurpose purpose() { return purpose; }
    public String provider() { return provider; }
    public String model() { return model; }
    public InvocationStatus status() { return status; }
    public TokenUsage usage() { return usage; }
    public BigDecimal cost() { return cost; }
    public String currency() { return currency; }
    public long latencyMillis() { return latencyMillis; }
    public String failureReason() { return failureReason; }
    public Instant occurredAt() { return occurredAt; }
}
