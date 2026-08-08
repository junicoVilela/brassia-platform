package br.com.brew.brassia.ai.adapter.inbound.web.dto;

import br.com.brew.brassia.ai.application.port.inbound.AnswerCommands.GroundedAnswer;
import br.com.brew.brassia.ai.domain.Grounding.VerifiedCitation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Contratos HTTP da resposta com evidência (RAG-002). */
public final class AnswerDtos {

    private AnswerDtos() {
    }

    /**
     * A pergunta.
     *
     * <p>{@code onDate} serve à pergunta sobre o passado: o que a ficha dizia quando o lote foi produzido é
     * outra pergunta que o que ela diz hoje, e as duas precisam ser possíveis.
     */
    public record AskRequest(
            @NotBlank @Size(max = 1000) String question,
            LocalDate onDate,
            UUID equipmentId) {
    }

    /**
     * A resposta.
     *
     * <p>Os três blocos de texto viajam separados e a interface não os junta: "o documento diz", "daí se
     * conclui" e "isto não está em fonte nenhuma" têm pesos diferentes para quem vai agir sobre a resposta.
     *
     * @param discarded citações que o modelo alegou e não conferiram. Chega ao cliente de propósito: é
     *                  informação sobre a confiabilidade daquela resposta, e esconder isso deixaria uma
     *                  resposta enfraquecida com a mesma aparência de uma resposta sólida.
     */
    public record AnswerView(
            boolean answered,
            String answer,
            List<CitationView> citations,
            List<String> inferences,
            List<String> limitations,
            int consultedSources,
            List<String> discarded) {

        public static AnswerView from(GroundedAnswer answer) {
            return new AnswerView(answer.answered(), answer.answer(),
                    answer.citations().stream().map(CitationView::from).toList(),
                    answer.inferences(), answer.limitations(), answer.consulted(), answer.discarded());
        }
    }

    /**
     * Uma citação conferida.
     *
     * <p>Os metadados vêm da fonte, não da resposta do modelo — deixar o modelo informar o título do que
     * citou seria dar a ele a chance de errar sobre um dado que o sistema já sabe. {@code effectiveOnDate}
     * falso é aviso, não erro: significa que a citação vem de versão substituída, o que muda como ela deve
     * ser lida.
     */
    public record CitationView(
            String documentCode,
            String title,
            String type,
            int version,
            boolean effectiveOnDate,
            int ordinal,
            String quote) {

        public static CitationView from(VerifiedCitation citation) {
            return new CitationView(citation.documentCode(), citation.title(), citation.type(),
                    citation.version(), citation.effectiveOnDate(), citation.ordinal(), citation.quote());
        }
    }
}
