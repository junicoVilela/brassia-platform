package br.com.brew.brassia.knowledge.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.knowledge.KnowledgeRetrieval;
import br.com.brew.brassia.knowledge.application.port.inbound.DocumentCommands;
import br.com.brew.brassia.knowledge.application.port.inbound.DocumentQueries;
import br.com.brew.brassia.knowledge.application.port.outbound.DocumentRepository;
import br.com.brew.brassia.knowledge.domain.Chunker;
import br.com.brew.brassia.knowledge.domain.Effectivity;
import br.com.brew.brassia.knowledge.domain.KnowledgeDocument;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Casos de uso da base de conhecimento (RAG-001). */
public final class DocumentHandlers {

    private DocumentHandlers() {
    }

    /**
     * Indexar um documento, encerrando a vigência da versão que ele substitui.
     *
     * <p>As duas escritas são um comando só, e é o que a história pede: uma janela em que duas versões
     * do mesmo documento estão vigentes é uma janela em que a recuperação devolve duas respostas
     * conflitantes — e é durante ela que alguém consulta.
     */
    public static final class Index implements DocumentCommands {

        private final DocumentRepository documents;
        private final Chunker chunker;
        private final AuditTrail audit;
        private final Clock clock;

        public Index(DocumentRepository documents, Chunker chunker, AuditTrail audit, Clock clock) {
            this.documents = Objects.requireNonNull(documents);
            this.chunker = Objects.requireNonNull(chunker);
            this.audit = Objects.requireNonNull(audit);
            this.clock = Objects.requireNonNull(clock);
        }

        @Override
        public KnowledgeDocument index(Request request) {
            Objects.requireNonNull(request, "request");

            // A versão é derivada, não informada: deixar quem chama escolher o número abriria a porta
            // para duas "versão 3" do mesmo código, e a citação deixaria de identificar o documento.
            var version = documents.highestVersionOf(request.breweryId(), request.code()) + 1;

            var document = KnowledgeDocument.index(request.breweryId(), request.type(), request.code(),
                    request.title(), version, Effectivity.from(request.effectiveFrom()),
                    request.requiredPermission(), request.equipmentId(), request.sourceUri(),
                    request.text(), chunker, request.actorId(), clock.instant());

            var superseded = documents
                    .currentVersionOf(request.breweryId(), request.code(), request.effectiveFrom())
                    .map(previous -> previous.supersededFrom(request.effectiveFrom()));

            documents.insert(document);
            superseded.ifPresent(documents::updateEffectivity);

            audit(document, superseded.orElse(null));
            return document;
        }

        private void audit(KnowledgeDocument document, KnowledgeDocument superseded) {
            var metadata = new LinkedHashMap<String, String>();
            metadata.put("type", document.type().name());
            metadata.put("code", document.code());
            metadata.put("version", String.valueOf(document.version()));
            metadata.put("effectiveFrom", document.effectivity().from().toString());
            metadata.put("requiredPermission", document.requiredPermission());
            metadata.put("chunks", String.valueOf(document.chunks().size()));
            // Checksum, e não texto: a auditoria precisa provar qual conteúdo entrou, não guardá-lo.
            metadata.put("checksum", document.checksum());
            if (superseded != null) {
                metadata.put("supersededVersion", String.valueOf(superseded.version()));
                metadata.put("supersededUntil", String.valueOf(superseded.effectivity().to()));
            }
            audit.record(AuditEvent.success(document.breweryId(), document.indexedBy(),
                    "knowledge.document.index", "knowledge_document", document.id().toString(), metadata));
        }
    }

    /** Recuperação: filtra por cervejaria, permissão e vigência dentro da própria busca. */
    public static final class Retrieval implements KnowledgeRetrieval {

        private final DocumentRepository documents;

        public Retrieval(DocumentRepository documents) {
            this.documents = Objects.requireNonNull(documents);
        }

        @Override
        public List<Evidence> search(Query query) {
            Objects.requireNonNull(query, "query");

            // Sem permissão nenhuma não há o que recuperar, e não vale ir ao banco descobrir isso.
            if (query.permissions().isEmpty()) {
                return List.of();
            }
            var terms = Chunker.termsOf(query.question());
            if (terms.isEmpty()) {
                // Pergunta sem palavra nenhuma — só pontuação ou espaço. Não vale ir ao banco.
                // Pergunta feita apenas de palavras de parada ("e o que é?") passa daqui e é o dicionário
                // português que a esvazia, devolvendo lista vazia pelo caminho normal. É o lado certo para
                // essa decisão: é o mesmo dicionário que indexou o texto.
                return List.of();
            }
            return documents.search(query, terms);
        }
    }

    /** Listagem para quem administra a base. */
    public static final class Listing implements DocumentQueries {

        private final DocumentRepository documents;

        public Listing(DocumentRepository documents) {
            this.documents = Objects.requireNonNull(documents);
        }

        @Override
        public List<KnowledgeDocument> visibleTo(UUID breweryId, Set<String> permissions) {
            Objects.requireNonNull(breweryId, "breweryId");
            if (permissions == null || permissions.isEmpty()) {
                return List.of();
            }
            return documents.findAll(breweryId, permissions);
        }
    }
}
