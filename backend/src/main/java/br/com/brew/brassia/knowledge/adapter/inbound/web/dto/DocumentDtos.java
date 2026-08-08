package br.com.brew.brassia.knowledge.adapter.inbound.web.dto;

import br.com.brew.brassia.knowledge.KnowledgeRetrieval;
import br.com.brew.brassia.knowledge.domain.DocumentType;
import br.com.brew.brassia.knowledge.domain.KnowledgeDocument;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Contratos HTTP da base de conhecimento (RAG-001). */
public final class DocumentDtos {

    private DocumentDtos() {
    }

    /**
     * Pedido de indexação.
     *
     * <p>A versão não vem daqui: é derivada do que já existe para o código. Deixar quem chama escolher o
     * número abriria a porta para duas "versão 3" do mesmo documento, e a citação deixaria de identificar
     * qual respondeu.
     */
    public record IndexRequest(
            @NotNull DocumentType type,
            @NotBlank @Size(max = 60) String code,
            @NotBlank @Size(max = 200) String title,
            @NotNull LocalDate effectiveFrom,
            @NotBlank @Size(max = 80) String requiredPermission,
            UUID equipmentId,
            @Size(max = 500) String sourceUri,
            // Um documento técnico inteiro cabe com folga; acima disto é upload de arquivo, que esta
            // história não faz (ver a pendência declarada no STATUS da sprint).
            @NotBlank @Size(max = 500_000) String text) {
    }

    /** O documento como a interface o lê. Sem o texto: aqui se administra a base, não se leem manuais. */
    public record DocumentView(
            UUID id,
            String type,
            String code,
            String title,
            int version,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            boolean current,
            String requiredPermission,
            UUID equipmentId,
            String sourceUri,
            int chunks,
            Instant indexedAt) {

        public static DocumentView from(KnowledgeDocument document) {
            return new DocumentView(document.id(), document.type().name(), document.code(),
                    document.title(), document.version(), document.effectivity().from(),
                    document.effectivity().to(), document.effectivity().open(),
                    document.requiredPermission(), document.equipmentId(), document.sourceUri(),
                    document.chunks().size(), document.indexedAt());
        }

        public static List<DocumentView> from(List<KnowledgeDocument> documents) {
            return documents.stream().map(DocumentView::from).toList();
        }
    }

    /**
     * Um trecho recuperado.
     *
     * <p>{@code untrusted} viaja sempre {@code true} e não é decoração: o texto foi escrito por
     * fabricante, laboratório ou fornecedor e pode conter instrução endereçada ao modelo. Quem consome
     * — a interface hoje, a RAG-002 amanhã — trata como conteúdo sobre o qual raciocinar, nunca como
     * ordem a seguir.
     */
    public record EvidenceView(
            UUID documentId,
            String code,
            String title,
            String type,
            int version,
            boolean effectiveOnDate,
            int ordinal,
            String text,
            double score,
            boolean untrusted) {

        public static EvidenceView from(KnowledgeRetrieval.Evidence evidence) {
            return new EvidenceView(evidence.documentId(), evidence.code(), evidence.title(),
                    evidence.type(), evidence.version(), evidence.effectiveOn(), evidence.ordinal(),
                    evidence.text(), evidence.score(), true);
        }

        public static List<EvidenceView> from(List<KnowledgeRetrieval.Evidence> evidence) {
            return evidence.stream().map(EvidenceView::from).toList();
        }
    }
}
