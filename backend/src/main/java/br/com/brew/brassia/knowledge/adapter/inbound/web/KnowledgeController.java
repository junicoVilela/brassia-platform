package br.com.brew.brassia.knowledge.adapter.inbound.web;

import br.com.brew.brassia.knowledge.KnowledgeRetrieval;
import br.com.brew.brassia.knowledge.adapter.inbound.web.dto.DocumentDtos;
import br.com.brew.brassia.knowledge.application.port.inbound.DocumentCommands;
import br.com.brew.brassia.knowledge.application.port.inbound.DocumentQueries;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * A base de conhecimento vista de fora (RAG-001).
 *
 * <p><strong>As permissões de quem pergunta vão para dentro da consulta.</strong> O controller não decide
 * o que a pessoa pode ver — ele entrega as permissões dela à recuperação, que filtra na própria busca. A
 * diferença importa: um filtro feito aqui protegeria só o que passa por HTTP, e a RAG-002 vai chamar essa
 * mesma recuperação de dentro do gateway, onde não há requisição nenhuma.
 *
 * <p>Por isso a leitura exige {@code knowledge.document.read} para <em>usar</em> a busca, mas o que ela
 * devolve depende do conjunto inteiro de permissões da pessoa: quem não tem a permissão de um laudo não
 * recebe trecho dele nem sabe que ele existe.
 */
@RestController
@RequestMapping("/api/v1/knowledge")
final class KnowledgeController {

    /** Teto de trechos por busca: o suficiente para responder, pouco o bastante para caber num prompt. */
    private static final int MAX_RESULTS = 8;

    private final DocumentCommands commands;
    private final DocumentQueries queries;
    private final KnowledgeRetrieval retrieval;
    private final Clock clock;

    KnowledgeController(DocumentCommands commands, DocumentQueries queries,
            KnowledgeRetrieval retrieval) {
        this.commands = commands;
        this.queries = queries;
        this.retrieval = retrieval;
        this.clock = Clock.systemUTC();
    }

    @GetMapping("/documents")
    List<DocumentDtos.DocumentView> documents(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("knowledge.document.read");
        return DocumentDtos.DocumentView.from(
                queries.visibleTo(principal.requireBrewery(), principal.permissions()));
    }

    @PostMapping("/documents")
    @ResponseStatus(HttpStatus.CREATED)
    DocumentDtos.DocumentView index(@AuthenticationPrincipal SecurityPrincipal principal,
            @Valid @RequestBody DocumentDtos.IndexRequest request) {
        principal.requirePermission("knowledge.document.index");
        return DocumentDtos.DocumentView.from(commands.index(new DocumentCommands.Request(
                principal.userId(), principal.requireBrewery(), request.type(), request.code(),
                request.title(), request.effectiveFrom(), request.requiredPermission(),
                request.equipmentId(), request.sourceUri(), request.text())));
    }

    /**
     * Busca de trechos.
     *
     * <p>{@code onDate} existe para a pergunta sobre o passado: "o que a FISPQ dizia quando o lote foi
     * produzido?" é diferente de "o que ela diz hoje", e uma base que só sabe responder sobre hoje não
     * serve para investigar nada. Ausente, vale hoje.
     */
    @GetMapping("/search")
    List<DocumentDtos.EvidenceView> search(@AuthenticationPrincipal SecurityPrincipal principal,
            @RequestParam String question,
            @RequestParam(required = false) LocalDate onDate,
            @RequestParam(required = false) UUID equipmentId) {
        principal.requirePermission("knowledge.document.read");
        var query = new KnowledgeRetrieval.Query(principal.requireBrewery(), principal.permissions(),
                question, onDate == null ? LocalDate.now(clock) : onDate, equipmentId, MAX_RESULTS);
        return DocumentDtos.EvidenceView.from(retrieval.search(query));
    }
}
