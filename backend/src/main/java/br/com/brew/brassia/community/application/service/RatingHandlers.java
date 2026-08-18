package br.com.brew.brassia.community.application.service;

import br.com.brew.brassia.community.domain.ReportOutcome;
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

    /**
     * O autor decide sobre a denúncia contra a própria publicação (DUV-COM-001).
     *
     * <p><strong>Não há moderador global, e a ausência é a decisão.</strong> Dar a alguém o poder de
     * esconder publicação de qualquer cervejaria é modelo de segurança, não detalhe de implementação — e
     * a plataforma não tem papel acima das casas. O que ela tem é o dono do conteúdo, que já responde por
     * ele lá fora.
     *
     * <p><strong>O óbvio contra-argumento, e por que ele não vence aqui:</strong> o autor julga causa
     * própria. Vence em parte — por isso julgar procedente <em>não</em> esconde nada sozinho, a denúncia
     * fica registrada com o desfecho, e quem denunciou continua vendo que ela existiu. O que se ganha é
     * que a acusação para de morrer no silêncio: hoje ela ficava aberta para sempre porque não havia
     * ninguém com autoridade para fechá-la.
     */
    @Transactional
    public void review(UUID breweryId, UUID publicationId, UUID reportId, UUID actor,
            ReportOutcome outcome, String note) {
        library.findOwned(breweryId, publicationId)
                .orElseThrow(() -> new UnknownPublicationException(publicationId));
        var report = ratings.findReport(publicationId, reportId)
                .orElseThrow(() -> new UnknownPublicationException(reportId));
        report.review(actor, Instant.now(), outcome, note);
        // Julgar procedente NÃO esconde: a ação sobre o conteúdo é ato separado, e encadear automático
        // faria a moderação executar antes de alguém decidir o que fazer (DEC-COM-007).
        ratings.review(report);
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
