package br.com.brew.brassia.community.application.port.outbound;

import br.com.brew.brassia.community.domain.AbuseReport;
import br.com.brew.brassia.community.domain.Rating;
import br.com.brew.brassia.community.domain.RatingSummary;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Avaliações e denúncias (COM-005).
 *
 * <p>{@link #rate} é UPSERT porque a nota se <strong>troca</strong>, e não acumula: deixar a mesma pessoa
 * avaliar duas vezes transformaria a média numa contagem de quem insistiu mais.
 */
public interface RatingRepository {

    void rate(Rating rating);

    Optional<Integer> myRating(UUID publicationId, UUID userId);

    RatingSummary summaryOf(UUID publicationId);

    void report(AbuseReport report);

    /** As denúncias contra uma publicação — a lista que o autor vê sobre o próprio conteúdo. */
    List<AbuseReport> reportsOf(UUID publicationId);
}
