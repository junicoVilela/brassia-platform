package br.com.brew.brassia.distribution.adapter.inbound.web;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.distribution.application.service.SyncHandlers;
import br.com.brew.brassia.distribution.domain.CoarseLocation;
import br.com.brew.brassia.distribution.domain.ConsentedMedia;
import br.com.brew.brassia.distribution.domain.DeliveryOutcome;
import br.com.brew.brassia.distribution.domain.OfflineOperation;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sincronização do aplicativo de distribuição (MOB-001).
 *
 * <p><strong>Responde 200, e não 201</strong>, mesmo criando coisas: o resultado do lote não é "criei" —
 * é uma lista em que cada item conta o que aconteceu com ele. Um 201 com corpo de desfechos mistos diria
 * ao aparelho que tudo entrou.
 */
@RestController
@RequestMapping("/api/v1/distribution/sync")
final class SyncController {

    private final SyncHandlers handlers;
    private final AuditTrail audit;

    SyncController(SyncHandlers handlers, AuditTrail audit) {
        this.handlers = Objects.requireNonNull(handlers);
        this.audit = Objects.requireNonNull(audit);
    }

    /**
     * Envia a fila acumulada offline.
     *
     * <p><strong>O reenvio devolve o mesmo resultado</strong>, e não cria outro: a idempotência é por
     * {@code (aparelho, clientOperationId)}, e o identificador vem do aparelho porque offline não há como
     * pedir um número ao servidor. A garantia é um índice único — o retry automático do aplicativo,
     * enquanto o sinal vai e volta, passaria por qualquer checagem prévia.
     *
     * <p><strong>Cada item tem desfecho próprio.</strong> Uma parada em conflito não derruba as outras
     * cinco: o entregador ficaria com o dia inteiro por sincronizar por causa de uma que o escritório
     * tocou.
     */
    @PostMapping
    List<OperationResult> sync(@AuthenticationPrincipal SecurityPrincipal principal,
            @Valid @RequestBody SyncRequest request) {
        principal.requirePermission("distribution.sync.write");
        var brewery = principal.requireBrewery();
        var resultados = handlers.sync(brewery, request.deviceId(), principal.userId(),
                request.operations().stream().map(OperationRequest::toPending).toList());
        audit.record(AuditEvent.success(brewery, principal.userId(), "distribution.sync",
                "distribution_device", request.deviceId().toString(),
                Map.of("operations", String.valueOf(resultados.size()),
                        "conflicts", String.valueOf(resultados.stream()
                                .filter(OfflineOperation::needsDecision).count()))));
        return resultados.stream().map(OperationResult::of).toList();
    }

    /**
     * A fila de quem precisa decidir.
     *
     * <p>Conflito não se resolve sozinho: último-a-escrever-ganha descartaria em silêncio o registro de
     * quem estava lá — ou o do escritório —, e nos dois casos alguém descobre semanas depois sem saber o
     * que perdeu.
     */
    @GetMapping("/conflicts")
    List<OperationResult> conflicts(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("distribution.load.read");
        return handlers.conflicts(principal.requireBrewery()).stream().map(OperationResult::of)
                .toList();
    }

    @GetMapping("/loads/{loadId}")
    List<OperationResult> ofLoad(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID loadId) {
        principal.requirePermission("distribution.load.read");
        return handlers.ofLoad(principal.requireBrewery(), loadId).stream().map(OperationResult::of)
                .toList();
    }

    record SyncRequest(@NotNull UUID deviceId,
            @NotEmpty @Valid List<OperationRequest> operations) {}

    /**
     * @param clientOperationId gerado no aparelho, antes de o servidor saber que a operação existe
     * @param sequence          a ordem do aparelho — aplicar fora dela entregaria antes de despachar
     * @param occurredAt        a hora do aparelho: quando a cerveja desceu, e não quando o pacote chegou
     */
    record OperationRequest(@NotNull UUID clientOperationId, @NotNull @Min(1) Integer sequence,
            @NotNull UUID loadId, @NotNull UUID stopId, @NotNull DeliveryOutcome outcome,
            @NotNull Instant occurredAt, @NotNull List<UUID> delivered,
            @NotNull List<UUID> collected, @Size(max = 1000) String note,
            DeliveryController.MediaConsent signatureConsent, BigDecimal latitude,
            BigDecimal longitude) {

        SyncHandlers.PendingOperation toPending() {
            return new SyncHandlers.PendingOperation(clientOperationId, sequence, loadId, stopId,
                    outcome, occurredAt, delivered, collected, note, toMedia(), toLocation());
        }

        private ConsentedMedia toMedia() {
            return signatureConsent == null ? null
                    : new ConsentedMedia(signatureConsent.kind(), signatureConsent.storageKey(),
                            signatureConsent.consentedByName(),
                            signatureConsent.consentedAt() == null ? Instant.now()
                                    : signatureConsent.consentedAt(),
                            signatureConsent.purpose());
        }

        private CoarseLocation toLocation() {
            // Arredondada aqui, como no registro online: a coordenada cheia não é gravada em lugar
            // nenhum, e o caminho do aplicativo não pode ser a exceção.
            return latitude == null || longitude == null ? null
                    : CoarseLocation.of(latitude, longitude);
        }
    }

    /**
     * @param status     {@code APPLIED}, {@code DUPLICATE}, {@code CONFLICTED} ou {@code REJECTED} —
     *                   "sincronizado" sozinho não distingue o que entrou do que foi recusado
     * @param resultId   a prova criada; no reenvio, é a mesma da primeira vez
     * @param clockAhead o relógio do aparelho estava à frente. Não invalida nada — o celular não se
     *                   ajusta sozinho no subsolo do bar —, mas quem lê a linha do tempo precisa saber
     */
    record OperationResult(UUID clientOperationId, int sequence, UUID stopId, String status,
            UUID resultId, String reason, Instant occurredAt, Instant receivedAt,
            boolean clockAhead) {

        static OperationResult of(OfflineOperation o) {
            return new OperationResult(o.clientOperationId(), o.sequence(), o.stopId(),
                    o.status().name(), o.resultId().orElse(null), o.reason().orElse(null),
                    o.occurredAt(), o.receivedAt(), o.clockAhead());
        }
    }
}
