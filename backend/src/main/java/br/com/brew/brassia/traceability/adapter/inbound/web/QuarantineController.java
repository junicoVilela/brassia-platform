package br.com.brew.brassia.traceability.adapter.inbound.web;

import br.com.brew.brassia.shared.security.SecurityPrincipal;
import br.com.brew.brassia.traceability.adapter.inbound.web.dto.TraceabilityViews;
import br.com.brew.brassia.traceability.application.port.inbound.QuarantineCommands;
import br.com.brew.brassia.traceability.application.port.inbound.QuarantineQueries;
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
 * Quarentena (FDS-002).
 *
 * <p>Abrir e liberar são alçadas <strong>separadas</strong>: quem contém não é necessariamente quem
 * decide que a investigação terminou. Foi a única forma de dar sentido a "liberação exige alçada" —
 * uma permissão só, usada nos dois comandos, tornaria a liberação tão barata quanto a abertura.
 */
@RestController
@RequestMapping("/api/v1/traceability/quarantines")
final class QuarantineController {

    /** Mesmo padrão da genealogia: a contenção mostra o que a investigação mostra. */
    private static final int DEFAULT_DEPTH = 6;

    private final QuarantineQueries queries;
    private final QuarantineCommands.Open open;
    private final QuarantineCommands.Release release;

    QuarantineController(QuarantineQueries queries, QuarantineCommands.Open open,
            QuarantineCommands.Release release) {
        this.queries = queries;
        this.open = open;
        this.release = release;
    }

    @GetMapping
    List<TraceabilityViews.QuarantineView> list(@AuthenticationPrincipal SecurityPrincipal principal,
            @RequestParam(defaultValue = "false") boolean onlyOpen) {
        principal.requirePermission("traceability.quarantine.read");
        return TraceabilityViews.QuarantineView.of(queries.list(principal.requireBrewery(), onlyOpen));
    }

    /** O alcance vem recalculado: o que está bloqueado hoje, não o que estava na abertura. */
    @GetMapping("/{id}")
    TraceabilityViews.QuarantineDetailView detail(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID id, @RequestParam(defaultValue = "" + DEFAULT_DEPTH) int depth) {
        principal.requirePermission("traceability.quarantine.read");
        return TraceabilityViews.QuarantineDetailView.of(
                queries.detail(principal.requireBrewery(), id, depth));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TraceabilityViews.QuarantineView open(@AuthenticationPrincipal SecurityPrincipal principal,
            @Valid @RequestBody TraceabilityViews.OpenQuarantineRequest request) {
        principal.requirePermission("traceability.quarantine.open");
        return TraceabilityViews.QuarantineView.of(open.handle(principal.userId(), principal.requireBrewery(),
                request.nodeType(), request.nodeId(), request.reason()));
    }

    @PostMapping("/{id}/release")
    void release(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody TraceabilityViews.ReleaseQuarantineRequest request) {
        principal.requirePermission("traceability.quarantine.release");
        release.handle(principal.userId(), principal.requireBrewery(), id, request.justification());
    }
}
