package br.com.brew.brassia.community.adapter.inbound.web;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.community.application.port.outbound.ContributionRepository;
import br.com.brew.brassia.community.application.service.ContributionHandlers;
import br.com.brew.brassia.community.domain.Contribution;
import br.com.brew.brassia.community.domain.ContributionKind;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * A conversa sobre uma publicação (COM-004).
 *
 * <p><strong>Aceitar uma sugestão não altera a receita nem o retrato publicado.</strong> Registra que o
 * autor concordou — aplicar é ato dele, na receita dele, e vira versão nova. É o que mantém as duas
 * decisões anteriores de pé: o retrato é congelado, e a receita de verdade é privada.
 *
 * <p><strong>A resposta não carrega a cervejaria de quem escreveu.</strong> Quem lê vê o nome, e não de
 * onde a pessoa é — a mesma regra do feed.
 */
@RestController
@RequestMapping("/api/v1/community")
final class ContributionController {

    private final ContributionHandlers handlers;
    private final ContributionRepository contributions;
    private final AuditTrail audit;

    ContributionController(ContributionHandlers handlers, ContributionRepository contributions,
            AuditTrail audit) {
        this.handlers = Objects.requireNonNull(handlers);
        this.contributions = Objects.requireNonNull(contributions);
        this.audit = Objects.requireNonNull(audit);
    }

    @GetMapping("/library/{publicationId}/contributions")
    List<ContributionView> list(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID publicationId) {
        principal.requirePermission("community.library.read");
        return contributions.listVisible(publicationId).stream()
                .map(ContributionController::view).toList();
    }

    @PostMapping("/library/{publicationId}/contributions")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> write(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID publicationId, @Valid @RequestBody WriteRequest request) {
        principal.requirePermission("community.contribution.write");
        var brewery = principal.requireBrewery();
        var id = handlers.write(brewery, principal.userId(), principal.displayName(), publicationId,
                request.kind(), request.body(), request.context());
        // O TEXTO não vai para a auditoria: ele já está na própria linha, e duplicá-lo num rastro que
        // sobrevive à moderação recriaria o conteúdo que alguém pode ter mandado esconder.
        audit.record(AuditEvent.success(brewery, principal.userId(), "community.contribution.write",
                "community.contribution", id.toString(), Map.of("kind", request.kind().name())));
        return Map.of("id", id);
    }

    @PostMapping("/contributions/{id}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void accept(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody(required = false) DecisionRequest request) {
        decide(principal, id, true, request);
    }

    @PostMapping("/contributions/{id}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void decline(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody(required = false) DecisionRequest request) {
        decide(principal, id, false, request);
    }

    /** Esconder da lista pública (COM-005). Não apaga: moderação precisa poder ser revista. */
    @PostMapping("/contributions/{id}/hide")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void hide(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID id) {
        principal.requirePermission("community.recipe.publish");
        var brewery = principal.requireBrewery();
        handlers.hide(brewery, principal.userId(), id);
        audit.record(AuditEvent.success(brewery, principal.userId(), "community.contribution.hide",
                "community.contribution", id.toString(), Map.of()));
    }

    private void decide(SecurityPrincipal principal, UUID id, boolean accept,
            DecisionRequest request) {
        // Decidir é do dono da publicação: quem responde pela receita lá fora é quem aceita ou recusa
        // o que sugerem sobre ela.
        principal.requirePermission("community.recipe.publish");
        var brewery = principal.requireBrewery();
        var note = request == null ? null : request.note();
        handlers.decide(brewery, principal.userId(), id, accept, note);
        audit.record(AuditEvent.success(brewery, principal.userId(),
                accept ? "community.contribution.accept" : "community.contribution.decline",
                "community.contribution", id.toString(), Map.of()));
    }

    private static ContributionView view(Contribution c) {
        return new ContributionView(c.id(), c.kind().name(), c.authorDisplayName(), c.body(),
                c.context().orElse(null), c.status().name(), c.createdAt(),
                c.decidedAt().orElse(null), c.decisionNote().orElse(null), c.isPending());
    }

    record WriteRequest(@NotNull ContributionKind kind, @NotBlank @Size(max = 2000) String body,
            @Size(max = 120) String context) {}

    /** A nota é opcional: obrigar texto faria o autor escrever "ok" para poder seguir. */
    record DecisionRequest(@Size(max = 500) String note) {}

    /**
     * O que se vê da conversa.
     *
     * <p>Sem cervejaria e sem identificador de usuário: quem lê vê o nome de quem escreveu, e nada que
     * permita cruzar aquela pessoa com outra coisa da plataforma.
     */
    record ContributionView(UUID id, String kind, String author, String body, String context,
            String status, Instant createdAt, Instant decidedAt, String decisionNote,
            boolean pending) {}
}
