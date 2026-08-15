package br.com.brew.brassia.community.adapter.inbound.web;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.community.application.port.outbound.ShareLinkRepository;
import br.com.brew.brassia.community.application.service.ShareLinkHandlers;
import br.com.brew.brassia.community.domain.PublicRecipeSnapshot;
import br.com.brew.brassia.community.domain.SharePermission;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Links compartilhados (COM-002).
 *
 * <p><strong>O token aparece uma vez.</strong> Ele volta na resposta da criação e nunca mais — o banco
 * guarda só o hash. Quem perder o valor cria outro link e revoga o primeiro; não há como recuperá-lo, e
 * poder recuperá-lo significaria que ele estava guardado em algum lugar.
 *
 * <p><strong>O acesso por token não eleva visibilidade.</strong> Fechar a publicação derruba todos os
 * links de uma vez, sem revogar um por um — é o botão de pânico do autor.
 */
@RestController
@RequestMapping("/api/v1/community")
final class ShareLinkController {

    private final ShareLinkHandlers links;
    private final ShareLinkRepository repository;
    private final AuditTrail audit;

    ShareLinkController(ShareLinkHandlers links, ShareLinkRepository repository, AuditTrail audit) {
        this.links = Objects.requireNonNull(links);
        this.repository = Objects.requireNonNull(repository);
        this.audit = Objects.requireNonNull(audit);
    }

    @PostMapping("/library/{publicationId}/links")
    @ResponseStatus(HttpStatus.CREATED)
    CreatedLink create(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID publicationId, @Valid @RequestBody CreateLinkRequest request) {
        principal.requirePermission("community.recipe.publish");
        var brewery = principal.requireBrewery();
        var created = links.create(brewery, principal.userId(), publicationId, request.permission(),
                request.label(), request.expiresAt());
        // O token NÃO vai para a auditoria: um rastro que guarda o segredo é o segredo guardado.
        audit.record(AuditEvent.success(brewery, principal.userId(), "community.link.create",
                "community.share_link", created.id().toString(),
                Map.of("publicationId", publicationId.toString(),
                        "permission", request.permission().name())));
        return new CreatedLink(created.id(), created.token());
    }

    @GetMapping("/library/{publicationId}/links")
    List<LinkView> list(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID publicationId) {
        principal.requirePermission("community.recipe.publish");
        var agora = Instant.now();
        return repository.listOfPublication(principal.requireBrewery(), publicationId).stream()
                .map(l -> new LinkView(l.id(), l.label().orElse(null), l.permission().name(),
                        l.createdAt(), l.expiresAt().orElse(null), l.revokedAt().orElse(null),
                        l.usableAt(agora)))
                .toList();
    }

    @PostMapping("/links/{id}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID id) {
        principal.requirePermission("community.recipe.publish");
        var brewery = principal.requireBrewery();
        links.revoke(brewery, principal.userId(), id);
        audit.record(AuditEvent.success(brewery, principal.userId(), "community.link.revoke",
                "community.share_link", id.toString(), Map.of()));
    }

    /**
     * Abrir uma publicação por link.
     *
     * <p>O token vai como parâmetro porque é assim que um link compartilhado funciona: ele é o endereço.
     * Exige autenticação como todo o resto — o link decide <em>o que</em> se vê, e não substitui
     * <em>quem</em> é.
     */
    @GetMapping("/shared")
    SharedView shared(@AuthenticationPrincipal SecurityPrincipal principal,
            @RequestParam String token) {
        principal.requirePermission("community.library.read");
        var resolved = links.resolve(token);
        var p = resolved.publication();
        return new SharedView(p.id(), p.title(), p.summary().orElse(null), p.authorDisplayName(),
                p.license().name(), p.license().label(), p.recipeVersion(), p.publishedAt(),
                resolved.mayComment(), p.snapshot());
    }

    record CreateLinkRequest(@NotNull SharePermission permission, @Size(max = 120) String label,
            Instant expiresAt) {}

    /** O token vem aqui, e só aqui. Guarde-o: ele não é recuperável. */
    record CreatedLink(UUID id, String token) {}

    /** A lista do autor mostra o estado — é o que torna a revogação uma decisão informada. */
    record LinkView(UUID id, String label, String permission, Instant createdAt, Instant expiresAt,
            Instant revokedAt, boolean usable) {}

    record SharedView(UUID id, String title, String summary, String author, String license,
            String licenseLabel, long recipeVersion, Instant publishedAt, boolean mayComment,
            PublicRecipeSnapshot recipe) {}
}
