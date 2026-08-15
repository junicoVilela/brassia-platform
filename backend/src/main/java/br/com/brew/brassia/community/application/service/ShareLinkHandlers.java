package br.com.brew.brassia.community.application.service;

import br.com.brew.brassia.community.application.port.outbound.PublishedRecipeRepository;
import br.com.brew.brassia.community.application.port.outbound.ShareLinkRepository;
import br.com.brew.brassia.community.domain.PublishedRecipe;
import br.com.brew.brassia.community.domain.ShareLink;
import br.com.brew.brassia.community.domain.SharePermission;
import br.com.brew.brassia.community.domain.UnknownPublicationException;
import br.com.brew.brassia.community.domain.UnknownShareLinkException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso do link compartilhado (COM-002). */
public class ShareLinkHandlers {

    private final ShareLinkRepository links;
    private final PublishedRecipeRepository library;

    public ShareLinkHandlers(ShareLinkRepository links, PublishedRecipeRepository library) {
        this.links = Objects.requireNonNull(links);
        this.library = Objects.requireNonNull(library);
    }

    /**
     * Cria o link e devolve o valor legível — a única vez em que ele existe fora da cabeça de quem
     * compartilha.
     */
    @Transactional
    public Created create(UUID breweryId, UUID actorId, UUID publicationId, SharePermission permission,
            String label, Instant expiresAt) {
        library.findOwned(breweryId, publicationId)
                .orElseThrow(() -> new UnknownPublicationException(publicationId));
        var token = ShareTokens.newToken();
        var link = ShareLink.create(UUID.randomUUID(), breweryId, publicationId,
                ShareTokens.hash(token), permission, label, Instant.now(), actorId, expiresAt);
        links.insert(link);
        return new Created(link.id(), token);
    }

    @Transactional
    public void revoke(UUID breweryId, UUID actorId, UUID linkId) {
        links.findOwned(breweryId, linkId).orElseThrow(UnknownShareLinkException::new);
        links.revoke(breweryId, linkId, Instant.now());
    }

    /**
     * Resolve um token em publicação, se ele der acesso.
     *
     * <p><strong>As duas condições, e o link nunca eleva.</strong> Um link válido para uma publicação
     * que o autor fechou não abre nada — fechar a publicação derruba todos os links de uma vez, sem
     * revogar um por um.
     */
    @Transactional(readOnly = true)
    public Resolved resolve(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new UnknownShareLinkException();
        }
        var link = links.findByTokenHash(ShareTokens.hash(rawToken))
                .orElseThrow(UnknownShareLinkException::new);
        var publication = library.findOwned(link.breweryId(), link.publicationId())
                .orElseThrow(UnknownShareLinkException::new);
        if (!link.grantsAccessTo(publication, Instant.now())) {
            throw new UnknownShareLinkException();
        }
        return new Resolved(publication, link.allowsComment());
    }

    /** O valor legível sai daqui e não volta: ele não é persistido em lugar nenhum. */
    public record Created(UUID id, String token) {}

    public record Resolved(PublishedRecipe publication, boolean mayComment) {}
}
