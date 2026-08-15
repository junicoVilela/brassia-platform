package br.com.brew.brassia.community.application.port.outbound;

import br.com.brew.brassia.community.domain.ShareLink;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistência dos links (COM-002).
 *
 * <p>{@link #findByTokenHash} não recebe cervejaria: quem chega com um link **não tem** cervejaria no
 * contexto — é justamente o caso de alguém de fora. O escopo vem da própria linha encontrada.
 */
public interface ShareLinkRepository {

    void insert(ShareLink link);

    void revoke(UUID breweryId, UUID id, java.time.Instant at);

    Optional<ShareLink> findByTokenHash(String tokenHash);

    Optional<ShareLink> findOwned(UUID breweryId, UUID id);

    List<ShareLink> listOfPublication(UUID breweryId, UUID publicationId);
}
