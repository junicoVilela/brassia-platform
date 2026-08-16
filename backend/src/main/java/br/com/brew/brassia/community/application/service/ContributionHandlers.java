package br.com.brew.brassia.community.application.service;

import br.com.brew.brassia.community.application.port.outbound.ContributionRepository;
import br.com.brew.brassia.community.application.port.outbound.PublishedRecipeRepository;
import br.com.brew.brassia.community.domain.Contribution;
import br.com.brew.brassia.community.domain.ContributionKind;
import br.com.brew.brassia.community.domain.UnknownPublicationException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso da conversa (COM-004).
 *
 * <p><strong>Escrever exige alcançar.</strong> Não se comenta o que não se pode ler — a mesma matriz de
 * visibilidade de sempre, e não uma regra nova. Decidir exige ser o dono da publicação.
 */
public class ContributionHandlers {

    private final ContributionRepository contributions;
    private final PublishedRecipeRepository library;

    public ContributionHandlers(ContributionRepository contributions,
            PublishedRecipeRepository library) {
        this.contributions = Objects.requireNonNull(contributions);
        this.library = Objects.requireNonNull(library);
    }

    @Transactional
    public UUID write(UUID breweryId, UUID actorId, String actorName, UUID publicationId,
            ContributionKind kind, String body, String context) {
        library.findForReader(publicationId, breweryId)
                .orElseThrow(() -> new UnknownPublicationException(publicationId));
        var contribution = Contribution.write(UUID.randomUUID(), publicationId, actorId, actorName,
                kind, body, context, Instant.now());
        contributions.insert(breweryId, contribution);
        return contribution.id();
    }

    /**
     * O dono aceita ou recusa.
     *
     * <p>A publicação é buscada por {@code findOwned}: só quem é dono decide o que sugerem sobre a
     * receita dele. Alguém de outra casa recebe 404 — e não 403, pelo mesmo motivo de sempre.
     */
    @Transactional
    public void decide(UUID breweryId, UUID actorId, UUID contributionId, boolean accept, String note) {
        var contribution = contributions.find(contributionId)
                .orElseThrow(() -> new UnknownPublicationException(contributionId));
        library.findOwned(breweryId, contribution.publicationId())
                .orElseThrow(() -> new UnknownPublicationException(contributionId));
        if (accept) {
            // Aceitar NÃO altera a receita nem o retrato: registra concordância. Aplicar é ato do autor.
            contribution.accept(actorId, Instant.now(), note);
        } else {
            contribution.decline(actorId, Instant.now(), note);
        }
        contributions.update(breweryId, contribution);
    }

    /** Esconde da lista pública (COM-005). Não apaga: moderação precisa poder ser revista. */
    @Transactional
    public void hide(UUID breweryId, UUID actorId, UUID contributionId) {
        var contribution = contributions.find(contributionId)
                .orElseThrow(() -> new UnknownPublicationException(contributionId));
        library.findOwned(breweryId, contribution.publicationId())
                .orElseThrow(() -> new UnknownPublicationException(contributionId));
        contribution.hide(Instant.now());
        contributions.update(breweryId, contribution);
    }
}
