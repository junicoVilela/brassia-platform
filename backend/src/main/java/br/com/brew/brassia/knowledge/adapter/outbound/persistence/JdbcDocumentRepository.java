package br.com.brew.brassia.knowledge.adapter.outbound.persistence;

import br.com.brew.brassia.knowledge.KnowledgeRetrieval;
import br.com.brew.brassia.knowledge.application.port.outbound.DocumentRepository;
import br.com.brew.brassia.knowledge.domain.DocumentChunk;
import br.com.brew.brassia.knowledge.domain.DocumentType;
import br.com.brew.brassia.knowledge.domain.Effectivity;
import br.com.brew.brassia.knowledge.domain.KnowledgeDocument;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Documentos e busca textual em PostgreSQL (RAG-001).
 *
 * <p><strong>Os três filtros vivem dentro do SQL da busca.</strong> Cervejaria, permissão e vigência não
 * são etapas depois do ranqueamento — são parte dele. Filtrar depois faria o limite de resultados mentir:
 * dez trechos ranqueados, sete invisíveis para quem perguntou, três entregues, e uma resposta pobre sem
 * nada que explicasse por quê.
 *
 * <p>É também o que garante o critério da história por construção: não existe caminho em que um trecho de
 * documento sem permissão chegue à memória do processo. Ele não é lido, não é ranqueado, não é truncado —
 * a consulta não o alcança.
 */
@Repository
class JdbcDocumentRepository implements DocumentRepository {

    private final JdbcClient jdbc;

    JdbcDocumentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(KnowledgeDocument document) {
        jdbc.sql("""
                INSERT INTO knowledge_document (id, brewery_id, type, code, title, version,
                        effective_from, effective_to, required_permission, equipment_id, source_uri,
                        checksum, indexed_by, indexed_at)
                VALUES (:id, :brewery, :type, :code, :title, :version, :from, :to, :permission,
                        :equipment, :source, :checksum, :by, :at)
                """)
                .param("id", document.id())
                .param("brewery", document.breweryId())
                .param("type", document.type().name())
                .param("code", document.code())
                .param("title", document.title())
                .param("version", document.version())
                .param("from", Date.valueOf(document.effectivity().from()))
                .param("to", document.effectivity().to() == null
                        ? null : Date.valueOf(document.effectivity().to()))
                .param("permission", document.requiredPermission())
                .param("equipment", document.equipmentId())
                .param("source", document.sourceUri())
                .param("checksum", document.checksum())
                .param("by", document.indexedBy())
                .param("at", Timestamp.from(document.indexedAt()))
                .update();

        for (var chunk : document.chunks()) {
            jdbc.sql("""
                    INSERT INTO knowledge_chunk (id, brewery_id, document_id, ordinal, content)
                    VALUES (:id, :brewery, :document, :ordinal, :content)
                    """)
                    .param("id", UUID.randomUUID())
                    .param("brewery", document.breweryId())
                    .param("document", document.id())
                    .param("ordinal", chunk.ordinal())
                    .param("content", chunk.text())
                    .update();
        }
    }

    @Override
    public void updateEffectivity(KnowledgeDocument document) {
        // Só a vigência. O texto de um documento indexado não se corrige — isso é a regra do agregado, e
        // aqui o SQL não oferece nem a possibilidade.
        jdbc.sql("""
                UPDATE knowledge_document SET effective_to = :to
                WHERE id = :id AND brewery_id = :brewery
                """)
                .param("id", document.id())
                .param("brewery", document.breweryId())
                .param("to", document.effectivity().to() == null
                        ? null : Date.valueOf(document.effectivity().to()))
                .update();
    }

    @Override
    public Optional<KnowledgeDocument> currentVersionOf(UUID breweryId, String code, LocalDate onDate) {
        return jdbc.sql("""
                SELECT id, brewery_id, type, code, title, version, effective_from, effective_to,
                        required_permission, equipment_id, source_uri, checksum, indexed_by, indexed_at
                FROM knowledge_document
                WHERE brewery_id = :brewery AND code = :code
                  AND effective_from <= :onDate
                  AND (effective_to IS NULL OR effective_to >= :onDate)
                ORDER BY version DESC LIMIT 1
                """)
                .param("brewery", breweryId).param("code", code).param("onDate", Date.valueOf(onDate))
                .query(this::mapDocument).optional();
    }

    @Override
    public int highestVersionOf(UUID breweryId, String code) {
        return jdbc.sql("""
                SELECT COALESCE(MAX(version), 0) FROM knowledge_document
                WHERE brewery_id = :brewery AND code = :code
                """)
                .param("brewery", breweryId).param("code", code)
                .query(Integer.class).single();
    }

    @Override
    public List<KnowledgeDocument> findAll(UUID breweryId, Set<String> permissions) {
        return jdbc.sql("""
                SELECT id, brewery_id, type, code, title, version, effective_from, effective_to,
                        required_permission, equipment_id, source_uri, checksum, indexed_by, indexed_at
                FROM knowledge_document
                WHERE brewery_id = :brewery AND required_permission IN (:permissions)
                ORDER BY indexed_at DESC
                """)
                .param("brewery", breweryId).param("permissions", permissions)
                .query(this::mapDocument).list();
    }

    /**
     * Busca textual ranqueada.
     *
     * <p><strong>Termos em OU, não em E.</strong> {@code plainto_tsquery} exigiria <em>todos</em> os
     * termos da pergunta, e o efeito disso é perverso: quanto mais detalhada a pergunta, menos ela
     * recupera. "torque de aperto do parafuso da bomba" deixaria de achar um procedimento que fala de
     * torque e aperto só porque ele não usa a palavra "parafuso" — e quem perguntou concluiria que não há
     * fonte. Com OU, todo documento que fala de alguma parte do assunto entra, e o ranqueamento põe na
     * frente quem fala de mais partes. É o comportamento que serve a uma pergunta em linguagem natural.
     *
     * <p><strong>Por que é seguro montar a consulta assim.</strong> {@code to_tsquery} interpreta
     * operadores, ao contrário do {@code plainto_tsquery} — o que normalmente seria uma porta de injeção
     * de sintaxe. Aqui não é, e o motivo é preciso: os termos vêm de {@code Chunker.termsOf}, que quebra a
     * pergunta em {@code [^\p{L}\p{N}]+} e portanto devolve <em>apenas</em> letras e dígitos. Nenhum
     * {@code &}, {@code |}, {@code !}, {@code :} ou parêntese sobrevive à normalização, logo não há
     * operador para injetar. A garantia é do domínio, e é por isso que ela mora lá e não aqui.
     *
     * <p>{@code ts_rank} pontua por frequência e proximidade dos termos. Não é probabilidade de nada, e a
     * porta publicada diz isso: é ordem relativa dentro desta consulta.
     *
     * <p>O filtro de equipamento aceita o que aponta para ele <em>e</em> o que não aponta para equipamento
     * nenhum: um procedimento geral de segurança responde sobre a bomba tanto quanto o manual dela.
     */
    @Override
    public List<KnowledgeRetrieval.Evidence> search(KnowledgeRetrieval.Query query, List<String> terms) {
        return jdbc.sql("""
                SELECT d.id, d.code, d.title, d.type, d.version, c.ordinal, c.content,
                        ts_rank(c.search_vector, to_tsquery('portuguese_unaccent', :terms)) AS score,
                        (d.effective_from <= :onDate
                            AND (d.effective_to IS NULL OR d.effective_to >= :onDate)) AS effective
                FROM knowledge_chunk c
                JOIN knowledge_document d ON d.id = c.document_id
                WHERE c.brewery_id = :brewery
                  AND d.required_permission IN (:permissions)
                  AND d.effective_from <= :onDate
                  AND (d.effective_to IS NULL OR d.effective_to >= :onDate)
                  -- O CAST não é enfeite: sem ele o PostgreSQL não consegue inferir o tipo de um
                  -- parâmetro que só aparece comparado a NULL, e a consulta falha com
                  -- "could not determine data type of parameter" justamente no caso comum — busca sem
                  -- equipamento informado.
                  AND (CAST(:equipment AS uuid) IS NULL
                       OR d.equipment_id = CAST(:equipment AS uuid)
                       OR d.equipment_id IS NULL)
                  AND c.search_vector @@ to_tsquery('portuguese_unaccent', :terms)
                ORDER BY score DESC, d.version DESC, c.ordinal ASC
                LIMIT :limit
                """)
                .param("brewery", query.breweryId())
                .param("permissions", query.permissions())
                .param("onDate", Date.valueOf(query.onDate()))
                .param("equipment", query.equipmentId())
                .param("terms", String.join(" | ", terms))
                .param("limit", query.limit())
                .query((rs, rowNum) -> new KnowledgeRetrieval.Evidence(
                        rs.getObject("id", UUID.class),
                        rs.getString("code"),
                        rs.getString("title"),
                        rs.getString("type"),
                        rs.getInt("version"),
                        rs.getBoolean("effective"),
                        rs.getInt("ordinal"),
                        rs.getString("content"),
                        rs.getDouble("score")))
                .list();
    }

    private KnowledgeDocument mapDocument(ResultSet rs, int rowNum) throws SQLException {
        var id = rs.getObject("id", UUID.class);
        var breweryId = rs.getObject("brewery_id", UUID.class);
        var to = rs.getDate("effective_to");
        return KnowledgeDocument.reconstitute(id, breweryId,
                DocumentType.valueOf(rs.getString("type")),
                rs.getString("code"),
                rs.getString("title"),
                rs.getInt("version"),
                new Effectivity(rs.getDate("effective_from").toLocalDate(),
                        to == null ? null : to.toLocalDate()),
                rs.getString("required_permission"),
                rs.getObject("equipment_id", UUID.class),
                rs.getString("source_uri"),
                rs.getString("checksum"),
                chunksOf(breweryId, id),
                rs.getObject("indexed_by", UUID.class),
                rs.getTimestamp("indexed_at").toInstant());
    }

    private List<DocumentChunk> chunksOf(UUID breweryId, UUID documentId) {
        return jdbc.sql("""
                SELECT ordinal, content FROM knowledge_chunk
                WHERE brewery_id = :brewery AND document_id = :document ORDER BY ordinal
                """)
                .param("brewery", breweryId).param("document", documentId)
                .query((rs, rowNum) -> new DocumentChunk(rs.getInt("ordinal"), rs.getString("content")))
                .list();
    }
}
