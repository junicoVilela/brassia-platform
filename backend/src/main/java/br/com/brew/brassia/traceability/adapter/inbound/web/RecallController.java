package br.com.brew.brassia.traceability.adapter.inbound.web;

import br.com.brew.brassia.shared.security.SecurityPrincipal;
import br.com.brew.brassia.traceability.adapter.inbound.web.dto.TraceabilityViews;
import br.com.brew.brassia.traceability.application.port.inbound.RecallCommands;
import br.com.brew.brassia.traceability.application.port.inbound.RecallQueries;
import jakarta.validation.Valid;
import java.util.List;
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
 * Recall (FDS-003).
 *
 * <p>Abrir deriva o escopo do grafo e materializa <strong>uma linha por destino alcançado</strong>.
 * Daí em diante o dossiê tem duas metades que se leem juntas e não se confundem: o escopo, que é
 * recalculado a cada leitura, e a comunicação, que é registro do que a cervejaria fez.
 */
@RestController
@RequestMapping("/api/v1/traceability/recalls")
final class RecallController {

    private static final int DEFAULT_DEPTH = 6;

    private final RecallQueries queries;
    private final RecallCommands.Open open;
    private final RecallCommands.RecordNotification notify;
    private final RecallCommands.Close close;

    RecallController(RecallQueries queries, RecallCommands.Open open,
            RecallCommands.RecordNotification notify, RecallCommands.Close close) {
        this.queries = queries;
        this.open = open;
        this.notify = notify;
        this.close = close;
    }

    @GetMapping
    List<TraceabilityViews.RecallView> list(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("traceability.recall.read");
        return TraceabilityViews.RecallView.of(queries.list(principal.requireBrewery()));
    }

    @GetMapping("/{id}")
    TraceabilityViews.RecallDossierView dossier(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID id, @RequestParam(defaultValue = "" + DEFAULT_DEPTH) int depth) {
        principal.requirePermission("traceability.recall.read");
        return TraceabilityViews.RecallDossierView.of(
                queries.dossier(principal.requireBrewery(), id, depth));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TraceabilityViews.RecallView open(@AuthenticationPrincipal SecurityPrincipal principal,
            @Valid @RequestBody TraceabilityViews.OpenRecallRequest request) {
        principal.requirePermission("traceability.recall.manage");
        return TraceabilityViews.RecallView.of(open.handle(principal.userId(), principal.requireBrewery(),
                request.nodeType(), request.nodeId(), request.reason()));
    }

    /** Registrar a comunicação é o comando mais frequente do recall: é o trabalho em si. */
    @PostMapping("/{id}/notifications/{notificationId}")
    void notify(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID id,
            @PathVariable UUID notificationId,
            @Valid @RequestBody TraceabilityViews.NotifyRequest request) {
        principal.requirePermission("traceability.recall.manage");
        notify.handle(principal.userId(), principal.requireBrewery(), id, notificationId,
                request.channel(), request.note());
    }

    @PostMapping("/{id}/close")
    void close(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody TraceabilityViews.CloseRecallRequest request) {
        principal.requirePermission("traceability.recall.manage");
        close.handle(principal.userId(), principal.requireBrewery(), id, request.summary());
    }
}
