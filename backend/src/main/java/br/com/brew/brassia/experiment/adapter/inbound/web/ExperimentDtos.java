package br.com.brew.brassia.experiment.adapter.inbound.web;

import br.com.brew.brassia.experiment.domain.Conclusion;
import br.com.brew.brassia.experiment.domain.ExperimentPlan;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Contratos do experimento (EXP-001). */
final class ExperimentDtos {

    private ExperimentDtos() {
    }

    /**
     * O experimento como sai na API.
     *
     * <p><strong>{@code limitations} não é opcional e não é filtrável.</strong> Quem consome a API recebe
     * as restrições junto com o resultado, sempre — inclusive antes de haver conclusão, porque elas são do
     * desenho e não da leitura. Uma resposta em que as limitações pudessem ser omitidas seria uma resposta
     * a partir da qual se monta um relatório que afirma mais do que o experimento sustenta.
     */
    record ExperimentResponse(
            UUID id,
            UUID recipeId,
            String hypothesis,
            UUID controlBatchId,
            UUID variantBatchId,
            FactorResponse isolatedVariable,
            List<FactorResponse> factors,
            Set<String> plannedMeasurements,
            boolean sensoryPlanned,
            boolean sensoryBlind,
            String status,
            List<LimitationResponse> limitations,
            ConclusionResponse conclusion,
            UUID plannedBy,
            Instant plannedAt) {

        static ExperimentResponse from(ExperimentPlan plan) {
            return new ExperimentResponse(
                    plan.id(),
                    plan.recipeId(),
                    plan.hypothesis(),
                    plan.controlBatchId(),
                    plan.variantBatchId(),
                    FactorResponse.from(plan.isolatedVariable()),
                    plan.factors().stream().map(FactorResponse::from).toList(),
                    plan.plannedMeasurements(),
                    plan.sensoryPlanned(),
                    plan.sensoryBlind(),
                    plan.status().name(),
                    plan.limitations().stream()
                            .map(l -> new LimitationResponse(l.name(), l.description())).toList(),
                    plan.conclusion().map(ConclusionResponse::from).orElse(null),
                    plan.plannedBy(),
                    plan.plannedAt());
        }
    }

    record FactorResponse(String name, String controlValue, String variantValue, boolean differs) {

        static FactorResponse from(br.com.brew.brassia.experiment.domain.ExperimentFactor factor) {
            return new FactorResponse(factor.name(), factor.controlValue(), factor.variantValue(),
                    factor.differs());
        }
    }

    /** A limitação viaja com a descrição: o código sozinho não informa quem lê o relatório. */
    record LimitationResponse(String code, String description) {
    }

    /**
     * @param supported compatível com a hipótese. Nunca "provado": um par de lotes não prova nada, e o
     *                  nome do campo é o que impede o relatório de dizer que provou.
     */
    record ConclusionResponse(boolean supported, String observation, UUID concludedBy,
            Instant concludedAt) {

        static ConclusionResponse from(Conclusion conclusion) {
            return new ConclusionResponse(conclusion.supported(), conclusion.observation(),
                    conclusion.concludedBy(), conclusion.concludedAt());
        }
    }
}
