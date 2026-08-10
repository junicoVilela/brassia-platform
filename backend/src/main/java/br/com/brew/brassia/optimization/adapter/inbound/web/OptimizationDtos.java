package br.com.brew.brassia.optimization.adapter.inbound.web;

import br.com.brew.brassia.optimization.domain.Candidate;
import br.com.brew.brassia.optimization.domain.OptimizationRun;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contratos da otimização (OPT-001). */
final class OptimizationDtos {

    private OptimizationDtos() {
    }

    /**
     * A corrida como sai na API.
     *
     * <p><strong>Método, versão da receita, versão do catálogo e semente viajam sempre.</strong> São o que
     * permite reproduzir o número — e um resultado que não se reproduz não se audita. {@code usesSeed}
     * acompanha a semente nula para distinguir "método não usa" de "esqueceram de gravar".
     *
     * <p>A explicação vem num campo separado das candidatas, e nunca dentro delas: misturá-las faria o
     * texto da IA parecer parte do cálculo.
     */
    record RunResponse(
            UUID id,
            UUID recipeId,
            int recipeVersion,
            String objective,
            List<ConstraintResponse> constraints,
            String method,
            String catalogVersion,
            Long seed,
            boolean usesSeed,
            boolean feasible,
            List<CandidateResponse> candidates,
            InfeasibleResponse infeasible,
            String explanation,
            UUID appliedRecipeVersionId,
            UUID requestedBy,
            Instant requestedAt) {

        static RunResponse from(OptimizationRun run) {
            return new RunResponse(
                    run.id(),
                    run.recipeId(),
                    run.recipeVersion(),
                    run.objective().name(),
                    run.constraints().stream()
                            .map(c -> new ConstraintResponse(c.kind().name(), c.minValue(),
                                    c.maxValue(), c.ingredientId()))
                            .toList(),
                    run.method().name(),
                    run.catalogVersion(),
                    run.seed().orElse(null),
                    run.method().usesSeed(),
                    run.feasible(),
                    run.candidates().stream().map(CandidateResponse::from).toList(),
                    run.infeasible()
                            .map(i -> new InfeasibleResponse(i.conflictingConstraints(),
                                    i.explanation()))
                            .orElse(null),
                    run.explanation().orElse(null),
                    run.appliedRecipeVersionId().orElse(null),
                    run.requestedBy(),
                    run.requestedAt());
        }
    }

    record ConstraintResponse(String kind, BigDecimal minValue, BigDecimal maxValue,
            UUID ingredientId) {
    }

    /**
     * @param tradeOffs o que piorou. Obrigatório e nunca omitido: uma alternativa que aparece só com o
     *                  ganho faz escolher sem saber o que se está trocando
     */
    record CandidateResponse(String label, List<SubstitutionResponse> substitutions,
            BigDecimal costPerLiter, BigDecimal estimatedIbu, BigDecimal estimatedColorEbc,
            BigDecimal score, List<TradeOffResponse> tradeOffs) {

        static CandidateResponse from(Candidate candidate) {
            return new CandidateResponse(candidate.label(),
                    candidate.substitutions().stream()
                            .map(s -> new SubstitutionResponse(s.fromIngredientId(), s.fromLabel(),
                                    s.toIngredientId(), s.toLabel(), s.quantity(), s.unit()))
                            .toList(),
                    candidate.costPerLiter(), candidate.estimatedIbu(), candidate.estimatedColorEbc(),
                    candidate.score(),
                    candidate.tradeOffs().stream()
                            .map(t -> new TradeOffResponse(t.dimension(), t.description(),
                                    t.originalValue(), t.candidateValue()))
                            .toList());
        }
    }

    record SubstitutionResponse(UUID fromIngredientId, String fromLabel, UUID toIngredientId,
            String toLabel, BigDecimal quantity, String unit) {
    }

    record TradeOffResponse(String dimension, String description, BigDecimal originalValue,
            BigDecimal candidateValue) {
    }

    /** Inviabilidade é resposta com conteúdo: diz quais restrições se contradizem. */
    record InfeasibleResponse(List<String> conflictingConstraints, String explanation) {
    }
}
