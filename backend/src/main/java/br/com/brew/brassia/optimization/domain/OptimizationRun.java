package br.com.brew.brassia.optimization.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Uma corrida do otimizador (OPT-001).
 *
 * <p><strong>Reprodutível por construção.</strong> Guarda método, versão das propriedades usadas e semente
 * quando existir. Um resultado sem isso não se reproduz — e um resultado que não se reproduz não se
 * audita: seis meses depois, ninguém consegue dizer se o número mudou porque o catálogo mudou ou porque o
 * solver mudou.
 *
 * <p><strong>A explicação da IA não altera o score, e a estrutura garante isso.</strong> {@code candidates}
 * é imutável e definido na criação; {@link #explain} só escreve num campo separado. Não existe caminho em
 * que gerar uma explicação recalcule alguma coisa — a IA descreve um resultado que já existia antes dela
 * ser chamada.
 */
public final class OptimizationRun {

    private final UUID id;
    private final UUID breweryId;
    private final UUID recipeId;
    private final int recipeVersion;
    private final Objective objective;
    private final List<OptimizationConstraint> constraints;
    private final SolverMethod method;
    private final String catalogVersion;
    private final Long seed;
    private final List<Candidate> candidates;
    private final Infeasible infeasible;
    private final UUID requestedBy;
    private final Instant requestedAt;

    private String explanation;
    private UUID appliedRecipeVersionId;

    private OptimizationRun(UUID id, UUID breweryId, UUID recipeId, int recipeVersion,
            Objective objective, List<OptimizationConstraint> constraints, SolverMethod method,
            String catalogVersion, Long seed, List<Candidate> candidates, Infeasible infeasible,
            String explanation, UUID appliedRecipeVersionId, UUID requestedBy, Instant requestedAt) {
        this.id = id;
        this.breweryId = breweryId;
        this.recipeId = recipeId;
        this.recipeVersion = recipeVersion;
        this.objective = objective;
        this.constraints = List.copyOf(constraints);
        this.method = method;
        this.catalogVersion = catalogVersion;
        this.seed = seed;
        this.candidates = List.copyOf(candidates);
        this.infeasible = infeasible;
        this.explanation = explanation;
        this.appliedRecipeVersionId = appliedRecipeVersionId;
        this.requestedBy = requestedBy;
        this.requestedAt = requestedAt;
    }

    /** Corrida com solução: uma ou mais alternativas ordenadas. */
    public static OptimizationRun solved(UUID id, UUID breweryId, UUID recipeId, int recipeVersion,
            Objective objective, List<OptimizationConstraint> constraints, SolverMethod method,
            String catalogVersion, Long seed, List<Candidate> candidates, UUID requestedBy,
            Instant requestedAt) {
        if (candidates.isEmpty()) {
            // Corrida "resolvida" sem candidata seria inviabilidade sem o nome — e sem a explicação que
            // a torna acionável.
            throw new IllegalArgumentException("corrida resolvida precisa de ao menos uma alternativa");
        }
        requireSeedCoherence(method, seed);
        return new OptimizationRun(id, breweryId, recipeId, recipeVersion, objective, constraints,
                method, catalogVersion, seed, candidates, null, null, null, requestedBy, requestedAt);
    }

    /** Corrida sem solução. Resposta legítima, com as restrições que se contradizem. */
    public static OptimizationRun infeasible(UUID id, UUID breweryId, UUID recipeId, int recipeVersion,
            Objective objective, List<OptimizationConstraint> constraints, SolverMethod method,
            String catalogVersion, Long seed, Infeasible infeasible, UUID requestedBy,
            Instant requestedAt) {
        Objects.requireNonNull(infeasible, "infeasible");
        requireSeedCoherence(method, seed);
        return new OptimizationRun(id, breweryId, recipeId, recipeVersion, objective, constraints,
                method, catalogVersion, seed, List.of(), infeasible, null, null, requestedBy,
                requestedAt);
    }

    public static OptimizationRun reconstitute(UUID id, UUID breweryId, UUID recipeId,
            int recipeVersion, Objective objective, List<OptimizationConstraint> constraints,
            SolverMethod method, String catalogVersion, Long seed, List<Candidate> candidates,
            Infeasible infeasible, String explanation, UUID appliedRecipeVersionId, UUID requestedBy,
            Instant requestedAt) {
        return new OptimizationRun(id, breweryId, recipeId, recipeVersion, objective, constraints,
                method, catalogVersion, seed, candidates, infeasible, explanation,
                appliedRecipeVersionId, requestedBy, requestedAt);
    }

    /**
     * Anexa a explicação em linguagem natural.
     *
     * <p><strong>Não recebe nem devolve score.</strong> A IA lê o resultado e o descreve; se pudesse
     * alterar o número, a explicação deixaria de explicar o cálculo e passaria a ser parte dele — e
     * ninguém saberia qual dos dois produziu a recomendação.
     */
    public void explain(String text) {
        var trimmed = Objects.requireNonNull(text, "text").trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("explicação vazia não explica nada");
        }
        this.explanation = trimmed;
    }

    /**
     * Marca que uma alternativa virou versão nova de receita.
     *
     * <p><strong>A corrida não aplica nada — ela registra que alguém aplicou.</strong> Quem cria a versão
     * é o módulo de receita, sob revisão humana. Se o otimizador pudesse escrever na receita, "revisado"
     * viraria um campo que alguém marca, em vez de um ato que alguém pratica.
     */
    public void markApplied(UUID recipeVersionId) {
        if (infeasible != null) {
            throw new IllegalStateException("não há alternativa a aplicar numa corrida inviável");
        }
        if (appliedRecipeVersionId != null) {
            throw new IllegalStateException("esta corrida já foi aplicada");
        }
        this.appliedRecipeVersionId = Objects.requireNonNull(recipeVersionId, "recipeVersionId");
    }

    private static void requireSeedCoherence(SolverMethod method, Long seed) {
        // Semente em método determinístico sugeriria variação que não existe; a falta dela num método
        // aleatório tornaria o resultado irreprodutível. Nos dois casos, o registro mentiria.
        if (method.usesSeed() && seed == null) {
            throw new IllegalArgumentException("método " + method + " exige semente para ser reproduzível");
        }
        if (!method.usesSeed() && seed != null) {
            throw new IllegalArgumentException("método " + method + " é determinístico e não usa semente");
        }
    }

    public boolean feasible() {
        return infeasible == null;
    }

    /** A melhor alternativa — a primeira, porque a lista já vem ordenada pelo score. */
    public Optional<Candidate> best() {
        return candidates.isEmpty() ? Optional.empty() : Optional.of(candidates.getFirst());
    }

    public UUID id() {
        return id;
    }

    public UUID breweryId() {
        return breweryId;
    }

    public UUID recipeId() {
        return recipeId;
    }

    public int recipeVersion() {
        return recipeVersion;
    }

    public Objective objective() {
        return objective;
    }

    public List<OptimizationConstraint> constraints() {
        return constraints;
    }

    public SolverMethod method() {
        return method;
    }

    public String catalogVersion() {
        return catalogVersion;
    }

    public Optional<Long> seed() {
        return Optional.ofNullable(seed);
    }

    public List<Candidate> candidates() {
        return candidates;
    }

    public Optional<Infeasible> infeasible() {
        return Optional.ofNullable(infeasible);
    }

    public Optional<String> explanation() {
        return Optional.ofNullable(explanation);
    }

    public Optional<UUID> appliedRecipeVersionId() {
        return Optional.ofNullable(appliedRecipeVersionId);
    }

    public UUID requestedBy() {
        return requestedBy;
    }

    public Instant requestedAt() {
        return requestedAt;
    }
}
