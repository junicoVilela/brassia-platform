package br.com.brew.brassia.ai.application.port.inbound;

import br.com.brew.brassia.ai.domain.Fact;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Avaliar um lote a partir dos fatos que o domínio calculou (AIA-002). */
public interface AssessmentCommands {

    /**
     * @throws br.com.brew.brassia.ai.domain.UnknownBatchException se o lote não existe nesta cervejaria
     */
    Assessment assess(UUID actorId, UUID breweryId, UUID batchId);

    /**
     * A avaliação.
     *
     * <p><strong>{@code facts} viaja junto da avaliação, e não é anexo.</strong> É o que permite conferir cada
     * afirmação sem sair da tela: o número está ali, com a unidade e com o serviço que o calculou. Uma
     * avaliação sem os fatos ao lado pediria confiança; com eles, pede leitura.
     *
     * @param usable     falso quando nada do que o modelo disse resistiu à conferência
     * @param summary    resumo, quando resistiu à conferência
     * @param risks      riscos aceitos, com severidade
     * @param assumptions o que o modelo supôs — não é fato e não é risco
     * @param facts      todos os fatos calculados, inclusive os que o modelo não usou
     * @param discarded  afirmações descartadas por número inventado ou fato inexistente, com o motivo
     */
    record Assessment(
            boolean usable,
            String summary,
            List<Risk> risks,
            List<String> assumptions,
            List<Fact> facts,
            List<String> discarded) {

        public Assessment {
            summary = summary == null ? "" : summary;
            risks = List.copyOf(risks == null ? List.of() : risks);
            assumptions = List.copyOf(assumptions == null ? List.of() : assumptions);
            facts = List.copyOf(Objects.requireNonNull(facts, "facts"));
            discarded = List.copyOf(discarded == null ? List.of() : discarded);
        }
    }

    /**
     * @param severity gravidade declarada pelo modelo; é juízo dele, não cálculo — e a tela diz isso
     * @param factRefs fatos que sustentam o risco, todos conferidos
     */
    record Risk(String statement, String severity, List<String> factRefs) {

        public Risk {
            factRefs = List.copyOf(factRefs == null ? List.of() : factRefs);
        }
    }
}
