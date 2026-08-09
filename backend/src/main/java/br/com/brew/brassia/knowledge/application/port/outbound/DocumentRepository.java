package br.com.brew.brassia.knowledge.application.port.outbound;

import br.com.brew.brassia.knowledge.KnowledgeRetrieval;
import br.com.brew.brassia.knowledge.domain.KnowledgeDocument;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Persistência e busca de documentos (RAG-001). */
public interface DocumentRepository {

    void insert(KnowledgeDocument document);

    /** Grava a vigência encerrada de uma versão substituída. O texto dela não muda. */
    void updateEffectivity(KnowledgeDocument document);

    /**
     * A versão vigente deste código na data, se houver.
     *
     * <p>Sem filtro de permissão de propósito: quem indexa precisa saber o que está substituindo, e essa
     * decisão não pode depender de o indexador ter permissão de leitura do documento antigo. É consulta
     * interna do comando, nunca exposta.
     */
    Optional<KnowledgeDocument> currentVersionOf(UUID breweryId, String code, java.time.LocalDate onDate);

    /** Maior versão já indexada para este código, ou zero. */
    int highestVersionOf(UUID breweryId, String code);

    List<KnowledgeDocument> findAll(UUID breweryId, Set<String> permissions);

    /**
     * Busca textual filtrada por cervejaria, permissão e vigência.
     *
     * <p>Os três filtros vão no mesmo SQL da busca, não em passos separados: filtrar depois de ranquear
     * faria o limite de resultados devolver menos do que pode: dez ranqueados, sete invisíveis, três
     * entregues — e a resposta sairia pobre sem que nada explicasse por quê.
     */
    List<KnowledgeRetrieval.Evidence> search(KnowledgeRetrieval.Query query, List<String> terms);
}
