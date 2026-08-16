package br.com.brew.brassia.community.application.service;

import br.com.brew.brassia.community.application.port.outbound.PublishedRecipeRepository;
import br.com.brew.brassia.community.application.port.outbound.RatingRepository;
import br.com.brew.brassia.community.domain.AbuseReport;
import br.com.brew.brassia.community.domain.Rating;
import br.com.brew.brassia.community.domain.ReportReason;
import br.com.brew.brassia.community.domain.SelfRatingException;
import br.com.brew.brassia.community.domain.UnknownPublicationException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Avaliar e denunciar (COM-005).
 *
 * <p><strong>Os dois exigem alcançar a publicação</strong> — a mesma matriz de visibilidade de sempre.
 * Não se avalia nem se denuncia o que não se pode ler.
 */
public class RatingHandlers {

    private final RatingRepository ratings;
    private final PublishedRecipeRepository library;

    public RatingHandlers(RatingRepository ratings, PublishedRecipeRepository library) {
        this.ratings = Objects.requireNonNull(ratings);
        this.library = Objects.requireNonNull(library);
    }

    @Transactional
    public void rate(UUID breweryId, UUID actorId, UUID publicationId, int value) {
        var publication = library.findForReader(publicationId, breweryId)
                .orElseThrow(() -> new UnknownPublicationException(publicationId));
        if (publication.authorUserId().equals(actorId)) {
            // A nota do autor não informa ninguém, e uma média que a inclui mede outra coisa.
            throw new SelfRatingException("avaliar");
        }
        ratings.rate(new Rating(publicationId, actorId, value, Instant.now()));
    }

    @Transactional
    public UUID report(UUID breweryId, UUID actorId, UUID publicationId, ReportReason reason,
            String note) {
        var publication = library.findForReader(publicationId, breweryId)
                .orElseThrow(() -> new UnknownPublicationException(publicationId));
        if (publication.authorUserId().equals(actorId)) {
            // Se o autor quer tirar do ar, o botão é despublicar — denunciar a si mesmo abriria um caso
            // para alguém revisar uma decisão que ele mesmo pode tomar.
            throw new SelfRatingException("denunciar");
        }
        var report = AbuseReport.open(UUID.randomUUID(), publicationId, actorId, reason, note,
                Instant.now());
        // Denunciar REGISTRA: não esconde nada. Uma denúncia que tirasse o conteúdo do ar seria uma
        // arma — qualquer um derrubaria a receita de um concorrente escrevendo três linhas.
        ratings.report(report);
        return report.id();
    }
}
