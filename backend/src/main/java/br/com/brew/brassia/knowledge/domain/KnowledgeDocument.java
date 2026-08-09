package br.com.brew.brassia.knowledge.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Um documento indexado e os trechos dele (RAG-001).
 *
 * <p><strong>Imutável depois de indexado.</strong> Corrigir o texto de um documento indexado apagaria a
 * base de uma resposta que já foi dada: alguém leu "0,15%" citando este documento, e amanhã o documento
 * diria "0,20%" sem que nada registrasse a mudança. Documento novo é versão nova — a mesma regra que a
 * receita publicada e o POP de limpeza já seguem neste sistema.
 *
 * <p><strong>A permissão exigida viaja com o documento.</strong> Não é uma verificação que a borda faz
 * e a recuperação confia: é atributo do documento, e a consulta filtra por ela. A diferença aparece no
 * dia em que a recuperação for chamada de outro lugar — de um job, de um evento — onde não existe borda
 * HTTP nenhuma para verificar nada.
 *
 * @param requiredPermission permissão sem a qual este documento não é recuperado — nem parcialmente
 * @param equipmentId        equipamento a que se refere, quando se refere a um; manual de bomba não
 *                           responde sobre a caldeira
 * @param sourceUri          onde está o original, quando existe; o texto indexado é o que se recupera
 * @param checksum           impressão do texto indexado, para detectar reindexação do mesmo conteúdo
 */
public final class KnowledgeDocument {

    private final UUID id;
    private final UUID breweryId;
    private final DocumentType type;
    private final String code;
    private final String title;
    private final int version;
    private final Effectivity effectivity;
    private final String requiredPermission;
    private final UUID equipmentId;
    private final String sourceUri;
    private final String checksum;
    private final List<DocumentChunk> chunks;
    private final UUID indexedBy;
    private final Instant indexedAt;

    private KnowledgeDocument(UUID id, UUID breweryId, DocumentType type, String code, String title,
            int version, Effectivity effectivity, String requiredPermission, UUID equipmentId,
            String sourceUri, String checksum, List<DocumentChunk> chunks, UUID indexedBy,
            Instant indexedAt) {
        this.id = Objects.requireNonNull(id);
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.type = Objects.requireNonNull(type, "tipo do documento é obrigatório");
        this.code = requireText(code, "código", 60);
        this.title = requireText(title, "título", 200);
        if (version <= 0) {
            throw new IllegalArgumentException("versão deve ser positiva");
        }
        this.version = version;
        this.effectivity = Objects.requireNonNull(effectivity, "vigência é obrigatória");
        this.requiredPermission = requireText(requiredPermission, "permissão exigida", 80);
        this.equipmentId = equipmentId;
        this.sourceUri = sourceUri;
        this.checksum = Objects.requireNonNull(checksum, "checksum");
        this.chunks = List.copyOf(Objects.requireNonNull(chunks, "trechos"));
        if (this.chunks.isEmpty()) {
            // Documento sem trecho é documento que não responde nada: aceitá-lo encheria a lista de
            // fontes com títulos que nunca poderiam ser citados.
            throw new IllegalArgumentException("um documento sem texto indexável não é indexável");
        }
        this.indexedBy = Objects.requireNonNull(indexedBy, "quem indexou é obrigatório");
        this.indexedAt = Objects.requireNonNull(indexedAt, "instante da indexação é obrigatório");
    }

    /**
     * Indexa um documento a partir do texto dele.
     *
     * <p>O corte em trechos acontece aqui, e não no adapter, porque é regra: quem monta o documento
     * decide o que vai poder ser citado como evidência dele.
     */
    public static KnowledgeDocument index(UUID breweryId, DocumentType type, String code, String title,
            int version, Effectivity effectivity, String requiredPermission, UUID equipmentId,
            String sourceUri, String text, Chunker chunker, UUID actorId, Instant at) {
        var pieces = chunker.split(text);
        var chunks = new java.util.ArrayList<DocumentChunk>(pieces.size());
        for (var i = 0; i < pieces.size(); i++) {
            chunks.add(new DocumentChunk(i, pieces.get(i)));
        }
        return new KnowledgeDocument(UUID.randomUUID(), breweryId, type, code, title, version, effectivity,
                requiredPermission, equipmentId, sourceUri, Checksum.of(text), chunks, actorId, at);
    }

    public static KnowledgeDocument reconstitute(UUID id, UUID breweryId, DocumentType type, String code,
            String title, int version, Effectivity effectivity, String requiredPermission, UUID equipmentId,
            String sourceUri, String checksum, List<DocumentChunk> chunks, UUID indexedBy,
            Instant indexedAt) {
        return new KnowledgeDocument(id, breweryId, type, code, title, version, effectivity,
                requiredPermission, equipmentId, sourceUri, checksum, chunks, indexedBy, indexedAt);
    }

    /**
     * Encerra a vigência deste documento porque outra versão o substitui.
     *
     * <p>Devolve uma nova instância: o documento não muda, a vigência dele muda — e é a única coisa que
     * pode mudar depois da indexação, porque "até quando valeu" só se sabe quando algo o substitui.
     */
    public KnowledgeDocument supersededFrom(LocalDate replacementFrom) {
        return new KnowledgeDocument(id, breweryId, type, code, title, version,
                effectivity.endedBefore(replacementFrom), requiredPermission, equipmentId, sourceUri,
                checksum, chunks, indexedBy, indexedAt);
    }

    /** Verdadeiro quando este documento vale na data — o que a recuperação pergunta por padrão. */
    public boolean effectiveOn(LocalDate date) {
        return effectivity.coversDate(date);
    }

    /**
     * Verdadeiro quando quem carrega estas permissões pode ver este documento.
     *
     * <p>Regra positiva: sem a permissão exigida, o documento não existe para o consultante. Não é
     * recuperado com o conteúdo escondido nem devolvido como "acesso negado" — as duas coisas contariam
     * que ele existe, e um título de laudo já é informação.
     */
    public boolean visibleTo(java.util.Set<String> permissions) {
        return permissions != null && permissions.contains(requiredPermission);
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório");
        }
        var trimmed = value.strip();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException(field + " excede " + max + " caracteres");
        }
        return trimmed;
    }

    public UUID id() { return id; }
    public UUID breweryId() { return breweryId; }
    public DocumentType type() { return type; }
    public String code() { return code; }
    public String title() { return title; }
    public int version() { return version; }
    public Effectivity effectivity() { return effectivity; }
    public String requiredPermission() { return requiredPermission; }
    public UUID equipmentId() { return equipmentId; }
    public String sourceUri() { return sourceUri; }
    public String checksum() { return checksum; }
    public List<DocumentChunk> chunks() { return chunks; }
    public UUID indexedBy() { return indexedBy; }
    public Instant indexedAt() { return indexedAt; }
}
