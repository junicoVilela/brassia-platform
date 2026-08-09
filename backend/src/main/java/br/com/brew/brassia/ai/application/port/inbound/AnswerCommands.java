package br.com.brew.brassia.ai.application.port.inbound;

import br.com.brew.brassia.ai.domain.Grounding.VerifiedCitation;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Perguntar ao copiloto com base nas fontes indexadas (RAG-002). */
public interface AnswerCommands {

    GroundedAnswer ask(Question question);

    /**
     * @param permissions permissões de quem pergunta; a recuperação filtra por elas, e por isso a resposta
     *                    de duas pessoas diferentes sobre a mesma pergunta pode legitimamente diferir
     * @param onDate      data de referência da vigência das fontes; hoje quando ausente
     */
    record Question(
            UUID actorId,
            UUID breweryId,
            Set<String> permissions,
            String text,
            LocalDate onDate,
            UUID equipmentId) {

        public Question {
            Objects.requireNonNull(actorId, "actorId é obrigatório: a IA não responde sem quem perguntou");
            Objects.requireNonNull(breweryId, "breweryId");
            permissions = Set.copyOf(Objects.requireNonNull(permissions, "permissions"));
            Objects.requireNonNull(onDate, "onDate");
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("pergunta vazia não é pergunta");
            }
            text = text.strip();
        }
    }

    /**
     * A resposta, com a fonte de cada afirmação separada do que foi inferido.
     *
     * <p><strong>Os três campos de texto são deliberadamente distintos e não devem ser juntados na
     * apresentação.</strong> "O documento diz", "daí se conclui" e "isto não está em fonte nenhuma" têm
     * pesos diferentes para quem vai agir sobre a resposta, e um parágrafo único apagaria a diferença — que
     * é justamente a informação mais importante quando o assunto é concentração química ou torque de aperto.
     *
     * @param answered    falso quando as fontes não sustentam resposta; então {@code answer} está vazio e
     *                    {@code limitations} explica o que falta
     * @param answer      a resposta, sustentada pelas citações conferidas
     * @param citations   citações <strong>conferidas</strong> contra as fontes recuperadas
     * @param inferences  o que o modelo concluiu e não está escrito em fonte nenhuma
     * @param limitations o que a resposta não cobre, e por quê
     * @param consulted   quantos trechos foram consultados — inclusive quando nada respondeu
     * @param discarded   citações descartadas na conferência, com o motivo; vazio é o caso normal
     */
    record GroundedAnswer(
            boolean answered,
            String answer,
            List<VerifiedCitation> citations,
            List<String> inferences,
            List<String> limitations,
            int consulted,
            List<String> discarded) {

        public GroundedAnswer {
            answer = answer == null ? "" : answer;
            citations = List.copyOf(citations == null ? List.of() : citations);
            inferences = List.copyOf(inferences == null ? List.of() : inferences);
            limitations = List.copyOf(limitations == null ? List.of() : limitations);
            discarded = List.copyOf(discarded == null ? List.of() : discarded);
        }

        /** Resposta que declara a limitação sem ter chamado o modelo. */
        public static GroundedAnswer withoutSources(String limitation) {
            return new GroundedAnswer(false, "", List.of(), List.of(), List.of(limitation), 0, List.of());
        }
    }
}
