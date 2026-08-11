package br.com.brew.brassia.traceability.adapter.inbound.web;

import br.com.brew.brassia.shared.security.SecurityPrincipal;
import br.com.brew.brassia.traceability.adapter.inbound.web.dto.TraceabilityViews;
import br.com.brew.brassia.traceability.application.port.inbound.DrillCommands;
import br.com.brew.brassia.traceability.application.port.inbound.DrillQueries;
import jakarta.validation.Valid;
import java.time.Instant;
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
 * Simulado de recall (FDS-004).
 *
 * <p>Treinar, não recolher: nenhum destes comandos cria expedição, move saldo, abre quarentena ou
 * gera pendência de comunicação. O simulado lê o mesmo grafo que o recall leria e cronometra a
 * cervejaria respondendo "onde está" — o relógio é o dela, não o do servidor.
 */
@RestController
@RequestMapping("/api/v1/traceability/recall-drills")
final class DrillController {

    private static final int DEFAULT_DEPTH = 6;

    private final DrillQueries queries;
    private final DrillCommands.Start start;
    private final DrillCommands.Finish finish;

    DrillController(DrillQueries queries, DrillCommands.Start start, DrillCommands.Finish finish) {
        this.queries = queries;
        this.start = start;
        this.finish = finish;
    }

    @GetMapping
    List<TraceabilityViews.DrillView> list(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("traceability.drill.read");
        return TraceabilityViews.DrillView.of(queries.list(principal.requireBrewery()), Instant.now());
    }

    @GetMapping("/{id}")
    TraceabilityViews.DrillReportView report(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID id, @RequestParam(defaultValue = "" + DEFAULT_DEPTH) int depth) {
        principal.requirePermission("traceability.drill.read");
        return TraceabilityViews.DrillReportView.of(
                queries.report(principal.requireBrewery(), id, depth));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TraceabilityViews.DrillView start(@AuthenticationPrincipal SecurityPrincipal principal,
            @Valid @RequestBody TraceabilityViews.StartDrillRequest request) {
        principal.requirePermission("traceability.drill.manage");
        var drill = start.handle(principal.userId(), principal.requireBrewery(), request.nodeType(),
                request.nodeId(), request.note());
        return TraceabilityViews.DrillView.of(drill, 0);
    }

    /** Encerrar é o comando que mede: quem diz quantas unidades achou é a equipe, não o sistema. */
    @PostMapping("/{id}/finish")
    void finish(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody TraceabilityViews.FinishDrillRequest request) {
        principal.requirePermission("traceability.drill.manage");
        finish.handle(principal.userId(), principal.requireBrewery(), id, request.unitsLocated(),
                request.summary(), request.correctiveActions(), request.nonConformityId(),
                request.capaActions() == null ? java.util.List.of()
                        : request.capaActions().stream()
                                .map(a -> new DrillCommands.Finish.Action(a.kind(), a.description(),
                                        a.owner(), a.dueOn()))
                                .toList());
    }
}
