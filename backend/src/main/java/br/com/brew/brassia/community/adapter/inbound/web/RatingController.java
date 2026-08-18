package br.com.brew.brassia.community.adapter.inbound.web;

import br.com.brew.brassia.community.domain.ReportOutcome;
import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.community.application.port.outbound.PublishedRecipeRepository;
import br.com.brew.brassia.community.application.port.outbound.RatingRepository;
import br.com.brew.brassia.community.application.service.RatingHandlers;
import br.com.brew.brassia.community.domain.ReportReason;
import br.com.brew.brassia.community.domain.UnknownPublicationException;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Avaliação e denúncia (COM-005).
 *
 * <p><strong>Não há endpoint de revisão, e a ausência é decisão registrada</strong> (DUV-COM-001).
 * "Executar moderação auditada" pressupõe um papel acima das cervejarias — o autor não pode julgar
 * denúncia contra a própria receita, e dar a alguém o poder de esconder publicação de qualquer casa é
 * decisão de modelo de segurança. O agregado já sabe ser revisado; falta decidir quem pode.
 */
@RestController
@RequestMapping("/api/v1/community/library/{publicationId}")
final class RatingController {

    private final RatingHandlers handlers;
    private final RatingRepository ratings;
    private final PublishedRecipeRepository library;
    private final AuditTrail audit;

    RatingController(RatingHandlers handlers, RatingRepository ratings,
            PublishedRecipeRepository library, AuditTrail audit) {
        this.handlers = Objects.requireNonNull(handlers);
        this.ratings = Objects.requireNonNull(ratings);
        this.library = Objects.requireNonNull(library);
        this.audit = Objects.requireNonNull(audit);
    }

    /**
     * A nota média, com quantos votaram.
     *
     * <p><strong>A média nunca viaja sozinha.</strong> "5,0" de uma avaliação e "5,0" de duzentas são o
     * mesmo número e significam coisas opostas — e {@code meaningful} diz à tela quando o número é
     * opinião em vez de reputação. Sem votos, {@code average} vem nulo: zero é a pior nota possível, e
     * uma receita nova nasceria parecendo péssima.
     */
    @GetMapping("/rating")
    RatingView rating(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID publicationId) {
        principal.requirePermission("community.library.read");
        library.findForReader(publicationId, principal.requireBrewery())
                .orElseThrow(() -> new UnknownPublicationException(publicationId));
        var summary = ratings.summaryOf(publicationId);
        return new RatingView(summary.average(), summary.count(), summary.meaningful(),
                ratings.myRating(publicationId, principal.userId()).orElse(null));
    }

    /** Uma nota por pessoa: repetir troca a anterior, e não acumula. */
    @PutMapping("/rating")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void rate(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID publicationId,
            @Valid @RequestBody RateRequest request) {
        principal.requirePermission("community.rating.write");
        var brewery = principal.requireBrewery();
        handlers.rate(brewery, principal.userId(), publicationId, request.value());
        audit.record(AuditEvent.success(brewery, principal.userId(), "community.rating.rate",
                "community.publication", publicationId.toString(),
                Map.of("value", String.valueOf(request.value()))));
    }

    /**
     * Denunciar abre um caso — <strong>não esconde nada</strong>.
     *
     * <p>Uma denúncia que tirasse o conteúdo do ar na hora seria uma arma: qualquer pessoa derrubaria a
     * receita de um concorrente escrevendo três linhas.
     */
    @PostMapping("/reports")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> report(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID publicationId, @Valid @RequestBody ReportRequest request) {
        principal.requirePermission("community.rating.write");
        var brewery = principal.requireBrewery();
        var id = handlers.report(brewery, principal.userId(), publicationId, request.reason(),
                request.note());
        audit.record(AuditEvent.success(brewery, principal.userId(), "community.report.open",
                "community.report", id.toString(), Map.of("reason", request.reason().name())));
        return Map.of("id", id);
    }

    /**
     * As denúncias contra a própria publicação.
     *
     * <p>Só o autor vê — e ele vê porque é o direito de resposta: saber do que está sendo acusado é o
     * mínimo antes de qualquer revisão existir. <strong>Sem quem denunciou:</strong> a identidade do
     * denunciante exposta ao denunciado transformaria a denúncia em convite à retaliação.
     */
    @GetMapping("/reports")
    List<ReportView> reports(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID publicationId) {
        principal.requirePermission("community.recipe.publish");
        library.findOwned(principal.requireBrewery(), publicationId)
                .orElseThrow(() -> new UnknownPublicationException(publicationId));
        return ratings.reportsOf(publicationId).stream()
                .map(r -> new ReportView(r.id(), r.reason().name(), r.note().orElse(null),
                        r.reportedAt(), r.reviewedAt().orElse(null),
                        r.outcome().map(Enum::name).orElse(null)))
                .toList();
    }

    /**
     * O autor decide sobre a denúncia contra a própria publicação (DUV-COM-001).
     *
     * <p><strong>Não há moderador global</strong> — dar a alguém o poder de esconder publicação de
     * qualquer casa é decisão de modelo de segurança, e a plataforma não tem papel acima das cervejarias.
     * O que ela tem é o dono do conteúdo, que já responde por ele lá fora. Alçada
     * `community.recipe.publish`: quem publica é quem decide o que dizem sobre o que publicou.
     *
     * <p><strong>Julgar procedente não esconde nada sozinho.</strong> A ação sobre o conteúdo é ato
     * separado — despublicar tem endpoint próprio —, e encadear automático faria a moderação executar
     * antes de alguém decidir o que fazer.
     *
     * <p><strong>O desfecho não se reescreve</strong>: revisar duas vezes é recusado, e a denúncia
     * improcedente continua registrada. Quem revisa também precisa poder ser revisado.
     */
    @PostMapping("/reports/{reportId}/review")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void review(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID publicationId, @PathVariable UUID reportId,
            @Valid @RequestBody ReviewRequest request) {
        principal.requirePermission("community.recipe.publish");
        var brewery = principal.requireBrewery();
        handlers.review(brewery, publicationId, reportId, principal.userId(), request.outcome(),
                request.note());
        audit.record(AuditEvent.success(brewery, principal.userId(), "community.report.review",
                "community.report", reportId.toString(),
                Map.of("outcome", request.outcome().name())));
    }

    record ReviewRequest(@NotNull ReportOutcome outcome, @Size(max = 1000) String note) {}

    record RateRequest(@NotNull @Min(1) @Max(5) Integer value) {}

    record ReportRequest(@NotNull ReportReason reason, @Size(max = 1000) String note) {}

    /**
     * @param meaningful falso quando há poucos votos — a tela mostra o número como opinião, e não como
     *                   reputação
     * @param myRating   nulo quando quem pergunta ainda não avaliou
     */
    record RatingView(BigDecimal average, int count, boolean meaningful, Integer myRating) {}

    /** Sem o denunciante: expô-lo ao denunciado seria convite à retaliação. */
    record ReportView(UUID id, String reason, String note, Instant reportedAt, Instant reviewedAt,
            String outcome) {}
}
