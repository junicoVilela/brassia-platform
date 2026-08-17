package br.com.brew.brassia.distribution.adapter.inbound.web;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.distribution.application.service.DeliveryHandlers;
import br.com.brew.brassia.distribution.domain.CoarseLocation;
import br.com.brew.brassia.distribution.domain.ConsentedMedia;
import br.com.brew.brassia.distribution.domain.DeliveryOutcome;
import br.com.brew.brassia.distribution.domain.ProofOfDelivery;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prova de entrega e coleta (LOG-002).
 *
 * <p><strong>Não há PUT nem DELETE aqui, e a ausência é a regra.</strong> Todo movimento é append-only e
 * se corrige por evento compensatório: uma prova de entrega reescrita parece original e diz outra coisa.
 */
@RestController
@RequestMapping("/api/v1/distribution")
final class DeliveryController {

    private final DeliveryHandlers handlers;
    private final AuditTrail audit;

    DeliveryController(DeliveryHandlers handlers, AuditTrail audit) {
        this.handlers = Objects.requireNonNull(handlers);
        this.audit = Objects.requireNonNull(audit);
    }

    /**
     * Registra o que aconteceu na parada — e move os vasilhames.
     *
     * <p>O que desceu vai para o cliente; o que foi recolhido volta como sujo, com o período do lote
     * fechado. É isso que faz o estoque contar certo sem ninguém digitar duas vezes.
     */
    @PostMapping("/loads/{loadId}/stops/{stopId}/proof")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> record(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID loadId, @PathVariable UUID stopId,
            @Valid @RequestBody ProofRequest request) {
        principal.requirePermission("distribution.delivery.record");
        var brewery = principal.requireBrewery();
        var id = handlers.record(brewery, loadId, stopId, request.outcome(),
                request.occurredAt() == null ? Instant.now() : request.occurredAt(),
                principal.userId(), request.delivered(), request.collected(), request.note(),
                request.toMedia(), request.toLocation());
        audit.record(AuditEvent.success(brewery, principal.userId(), "distribution.delivery.record",
                "distribution_proof", id.toString(),
                Map.of("outcome", request.outcome().name(),
                        "delivered", String.valueOf(request.delivered().size()),
                        "collected", String.valueOf(request.collected().size()))));
        return Map.of("id", id);
    }

    /**
     * Corrige a prova — <strong>sem apagar a anterior</strong>.
     *
     * <p>Alçada própria e crítica: corrigir mexe no que já foi dado como fato. As duas ficam, e o caminho
     * até a última palavra continua legível — que é o que separa uma correção de um encobrimento.
     *
     * <p>O <strong>vasilhame não é remexido aqui</strong>: um keg marcado como entregue que na verdade
     * voltou precisa ser movido por quem o tem na mão, e adivinhar a transição a partir da correção
     * produziria estados que ninguém observou.
     */
    @PostMapping("/stops/{stopId}/proof/correction")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> correct(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID stopId, @Valid @RequestBody CorrectionRequest request) {
        principal.requirePermission("distribution.delivery.correct");
        var brewery = principal.requireBrewery();
        var id = handlers.correct(brewery, stopId, request.outcome(),
                request.occurredAt() == null ? Instant.now() : request.occurredAt(),
                principal.userId(), request.delivered(), request.collected(), request.reason());
        audit.record(AuditEvent.success(brewery, principal.userId(), "distribution.delivery.correct",
                "distribution_proof", id.toString(), Map.of("reason", request.reason())));
        return Map.of("id", id);
    }

    /** Original e correção, na ordem em que foram registradas. */
    @GetMapping("/stops/{stopId}/proof")
    List<ProofView> ofStop(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID stopId) {
        principal.requirePermission("distribution.load.read");
        return handlers.ofStop(principal.requireBrewery(), stopId).stream().map(ProofView::of).toList();
    }

    @GetMapping("/loads/{loadId}/proofs")
    List<ProofView> ofLoad(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID loadId) {
        principal.requirePermission("distribution.load.read");
        return handlers.ofLoad(principal.requireBrewery(), loadId).stream().map(ProofView::of).toList();
    }

    /**
     * @param signatureConsent nulo quando não houve consentimento — e a entrega acontece do mesmo jeito.
     *                         O cliente que não quer assinar continua recebendo a cerveja
     * @param latitude         chega como veio do aparelho e é <strong>arredondada aqui</strong>; a
     *                         coordenada cheia não é gravada em lugar nenhum
     */
    record ProofRequest(@NotNull DeliveryOutcome outcome, Instant occurredAt,
            @NotNull List<UUID> delivered, @NotNull List<UUID> collected,
            @Size(max = 1000) String note, MediaConsent signatureConsent,
            BigDecimal latitude, BigDecimal longitude) {

        ConsentedMedia toMedia() {
            return signatureConsent == null ? null
                    : new ConsentedMedia(signatureConsent.kind(), signatureConsent.storageKey(),
                            signatureConsent.consentedByName(),
                            signatureConsent.consentedAt() == null ? Instant.now()
                                    : signatureConsent.consentedAt(),
                            signatureConsent.purpose());
        }

        CoarseLocation toLocation() {
            return latitude == null || longitude == null ? null
                    : CoarseLocation.of(latitude, longitude);
        }
    }

    /** Sem estes campos não há mídia: o consentimento não é uma caixinha, é o que autoriza guardar. */
    record MediaConsent(@NotNull ConsentedMedia.MediaKind kind, @NotNull String storageKey,
            @NotNull String consentedByName, Instant consentedAt, @NotNull String purpose) {}

    record CorrectionRequest(@NotNull DeliveryOutcome outcome, Instant occurredAt,
            @NotNull List<UUID> delivered, @NotNull List<UUID> collected,
            @NotNull @Size(min = 1, max = 1000) String reason) {}

    /**
     * @param mediaKind    só o tipo e a finalidade saem na leitura. A chave do arquivo não viaja na
     *                     listagem: quem precisa do binário pede por um caminho próprio, e assim a
     *                     assinatura de alguém não vaza num JSON de rotina
     * @param correctsProofId quando presente, esta é a correção — e a original continua na lista
     */
    record ProofView(UUID id, UUID stopId, String outcome, Instant occurredAt, UUID recordedBy,
            List<UUID> delivered, List<UUID> collected, String note, boolean outsideWindow,
            String mediaKind, String mediaPurpose, String consentedByName, BigDecimal latitude,
            BigDecimal longitude, UUID correctsProofId) {

        static ProofView of(ProofOfDelivery p) {
            var media = p.media().orElse(null);
            var lugar = p.location().orElse(null);
            return new ProofView(p.id(), p.stopId(), p.outcome().name(), p.occurredAt(),
                    p.recordedBy(), p.deliveredContainerIds(), p.collectedContainerIds(),
                    p.note().orElse(null), p.outsideWindow(),
                    media == null ? null : media.kind().name(),
                    media == null ? null : media.purpose(),
                    media == null ? null : media.consentedByName(),
                    lugar == null ? null : lugar.latitude(),
                    lugar == null ? null : lugar.longitude(),
                    p.correctsProofId().orElse(null));
        }
    }
}
