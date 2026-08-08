package br.com.brew.brassia.knowledge.application.port.inbound;

import br.com.brew.brassia.knowledge.domain.KnowledgeDocument;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Consultar a base de conhecimento (RAG-001). */
public interface DocumentQueries {

    /**
     * Documentos que esta pessoa pode ver, do mais recente para o mais antigo.
     *
     * <p>Inclui as versões superadas: quem administra a base precisa ver o histórico para saber o que
     * foi substituído e quando. É por isso que a lista não é filtrada por vigência — só por permissão,
     * que é o filtro que não se negocia.
     */
    List<KnowledgeDocument> visibleTo(UUID breweryId, Set<String> permissions);
}
