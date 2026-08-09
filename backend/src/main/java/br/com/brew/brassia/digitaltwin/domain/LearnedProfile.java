package br.com.brew.brassia.digitaltwin.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * O que o histórico ensinou sobre uma receita (DTW-001).
 *
 * <p><strong>Um perfil é um resumo datado de uma amostra nomeada, não uma verdade sobre a cervejaria.</strong>
 * Ele guarda quais lotes foram observados, quando foi calculado e o que saiu — e as três coisas juntas são o
 * que o torna auditável: qualquer pessoa pode refazer a conta e chegar ao mesmo número, ou apontar que a
 * amostra tinha um lote que não deveria estar lá.
 *
 * <p><strong>Versionado, nunca sobrescrito.</strong> Um perfil calculado em maio guiou decisões em maio;
 * recalcular em agosto e apagar o anterior faria essas decisões parecerem tomadas sobre números que nunca
 * existiram. Versão nova é linha nova — a mesma regra que vale para receita publicada e para documento
 * indexado.
 *
 * <p><strong>O que este objeto deliberadamente não faz: explicar.</strong> Ele diz que a eficiência
 * observada nesta amostra foi 74% com tal faixa; não diz que foi 74% <em>porque</em> a moagem mudou. A
 * associação entre um número e uma causa não está nos dados que ele leu, e apresentá-la seria transformar
 * correlação em causa — que é o erro que esta sprint pede explicitamente para testar. Quem investiga a
 * causa é a pessoa, com o perfil na mão.
 */
public final class LearnedProfile {

    private final UUID id;
    private final UUID breweryId;
    private final UUID recipeId;
    private final int version;
    private final Map<ProfileMetric, Estimate> estimates;
    private final List<UUID> observedBatchIds;
    private final UUID computedBy;
    private final Instant computedAt;

    private LearnedProfile(UUID id, UUID breweryId, UUID recipeId, int version,
            Map<ProfileMetric, Estimate> estimates, List<UUID> observedBatchIds, UUID computedBy,
            Instant computedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.recipeId = Objects.requireNonNull(recipeId, "recipeId");
        this.version = version;
        this.estimates = Map.copyOf(estimates);
        this.observedBatchIds = List.copyOf(observedBatchIds);
        this.computedBy = Objects.requireNonNull(computedBy, "computedBy");
        this.computedAt = Objects.requireNonNull(computedAt, "computedAt");
    }

    /**
     * Calcula um perfil a partir das observações de uma amostra.
     *
     * <p>A amostra é <strong>informada</strong>, não descoberta. Parece uma limitação e é uma propriedade:
     * quem conhece a operação pode excluir o lote em que a bomba falhou, e o perfil registra exatamente
     * quais lotes entraram — o que torna a exclusão visível em vez de silenciosa. Um cálculo sobre "todos
     * os lotes" esconderia essa decisão dentro de uma consulta.
     *
     * @param observations valores observados por métrica; métrica sem observação suficiente vira
     *                     {@link Estimate#insufficient}, e não desaparece do perfil — ausência declarada é
     *                     informação, ausência silenciosa é um buraco.
     */
    public static LearnedProfile compute(UUID breweryId, UUID recipeId, int version,
            Map<ProfileMetric, List<java.math.BigDecimal>> observations, List<UUID> observedBatchIds,
            UUID actorId, Instant now) {
        Objects.requireNonNull(observations, "observations");
        if (observedBatchIds == null || observedBatchIds.isEmpty()) {
            throw new IllegalArgumentException("um perfil precisa de ao menos um lote observado");
        }
        if (version < 1) {
            throw new IllegalArgumentException("versão do perfil começa em 1");
        }

        var estimates = new java.util.EnumMap<ProfileMetric, Estimate>(ProfileMetric.class);
        for (var metric : ProfileMetric.values()) {
            estimates.put(metric, Estimate.from(observations.getOrDefault(metric, List.of())));
        }
        return new LearnedProfile(UUID.randomUUID(), breweryId, recipeId, version, estimates,
                observedBatchIds, actorId, now);
    }

    public static LearnedProfile reconstitute(UUID id, UUID breweryId, UUID recipeId, int version,
            Map<ProfileMetric, Estimate> estimates, List<UUID> observedBatchIds, UUID computedBy,
            Instant computedAt) {
        return new LearnedProfile(id, breweryId, recipeId, version, estimates, observedBatchIds, computedBy,
                computedAt);
    }

    public Estimate estimateOf(ProfileMetric metric) {
        return estimates.get(metric);
    }

    /**
     * Se alguma métrica dá para usar.
     *
     * <p>Um perfil em que nada é estimável ainda é gravado. Parece inútil e não é: ele registra que a
     * tentativa foi feita, sobre quais lotes, e que não deu — o que é a resposta certa para quem pergunta
     * "por que não tenho perfil desta receita?".
     */
    public boolean hasAnyUsableEstimate() {
        return estimates.values().stream().anyMatch(Estimate::usable);
    }

    public UUID id() { return id; }
    public UUID breweryId() { return breweryId; }
    public UUID recipeId() { return recipeId; }
    public int version() { return version; }
    public Map<ProfileMetric, Estimate> estimates() { return estimates; }
    public List<UUID> observedBatchIds() { return observedBatchIds; }
    public UUID computedBy() { return computedBy; }
    public Instant computedAt() { return computedAt; }
}
