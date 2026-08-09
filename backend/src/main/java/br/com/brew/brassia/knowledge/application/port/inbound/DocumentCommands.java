package br.com.brew.brassia.knowledge.application.port.inbound;

import br.com.brew.brassia.knowledge.domain.DocumentType;
import br.com.brew.brassia.knowledge.domain.KnowledgeDocument;
import java.time.LocalDate;
import java.util.UUID;

/** Indexar documento na base de conhecimento (RAG-001). */
public interface DocumentCommands {

    /**
     * Indexa um documento e, se já houver versão vigente com o mesmo código, encerra a vigência dela.
     *
     * <p><strong>A substituição é parte do mesmo comando, e é o ponto da história.</strong> Indexar a
     * FISPQ nova sem encerrar a antiga deixaria duas vigentes sobre o mesmo produto, e a recuperação
     * devolveria as duas com concentrações diferentes sem meio de saber qual vale. Encerrar depois, num
     * segundo comando, deixaria essa janela aberta — e é justamente durante ela que alguém consulta.
     *
     * <p>A versão anterior <strong>não é apagada</strong>: ela continua recuperável para pergunta sobre
     * o passado. O que muda é até quando ela valeu.
     */
    KnowledgeDocument index(Request request);

    /**
     * @param actorId            quem indexou; documento entra na base por decisão de alguém
     * @param breweryId          cervejaria dona do documento
     * @param type               espécie do documento
     * @param code               código estável entre versões — é o que liga a versão nova à antiga
     * @param title              título legível
     * @param effectiveFrom      início da vigência; a versão anterior é encerrada no dia anterior
     * @param requiredPermission permissão sem a qual o documento não é recuperado
     * @param equipmentId        equipamento a que se refere, quando se refere a um
     * @param sourceUri          onde vive o original, quando existe
     * @param text               o texto a indexar
     */
    record Request(
            UUID actorId,
            UUID breweryId,
            DocumentType type,
            String code,
            String title,
            LocalDate effectiveFrom,
            String requiredPermission,
            UUID equipmentId,
            String sourceUri,
            String text) {
    }
}
