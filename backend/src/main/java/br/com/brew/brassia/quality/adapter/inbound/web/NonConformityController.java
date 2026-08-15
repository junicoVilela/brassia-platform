package br.com.brew.brassia.quality.adapter.inbound.web;

import br.com.brew.brassia.quality.adapter.inbound.web.dto.QualityDtos;
import br.com.brew.brassia.quality.adapter.inbound.web.dto.QualityViews;
import br.com.brew.brassia.quality.application.port.inbound.NonConformityCommands;
import br.com.brew.brassia.quality.application.port.inbound.QualityQueries;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Não conformidade e CAPA (QLT-002): conter, investigar, agir, verificar e encerrar.
 *
 * <p>Cada fase tem o seu endpoint em vez de um único PUT com o objeto inteiro: a ordem das fases é
 * regra de negócio, e um PUT genérico permitiria enviar investigação e verificação de uma vez,
 * exatamente o atalho que a história existe para impedir.
 *
 * <p>Encerrar é alçada própria (`quality.nc.close`) — é o ato que declara o problema resolvido.
 */
@RestController
@RequestMapping("/api/v1/quality/non-conformities")
final class NonConformityController {

    private final NonConformityCommands.Open open;
    private final NonConformityCommands.Contain contain;
    private final NonConformityCommands.Investigate investigate;
    private final NonConformityCommands.PlanAction planAction;
    private final NonConformityCommands.CompleteAction completeAction;
    private final NonConformityCommands.Verify verify;
    private final NonConformityCommands.Close close;
    private final QualityQueries queries;

    NonConformityController(NonConformityCommands.Open open, NonConformityCommands.Contain contain,
            NonConformityCommands.Investigate investigate, NonConformityCommands.PlanAction planAction,
            NonConformityCommands.CompleteAction completeAction, NonConformityCommands.Verify verify,
            NonConformityCommands.Close close, QualityQueries queries) {
        this.open = open;
        this.contain = contain;
        this.investigate = investigate;
        this.planAction = planAction;
        this.completeAction = completeAction;
        this.verify = verify;
        this.close = close;
        this.queries = queries;
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    @GetMapping
    List<QualityViews.NonConformityView> list(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("quality.nc.read");
        var on = today();
        return queries.nonConformities(principal.requireBrewery()).stream()
                .map(nc -> QualityViews.NonConformityView.from(nc, on))
                .toList();
    }

    @GetMapping("/{id}")
    QualityViews.NonConformityView get(@PathVariable UUID id,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("quality.nc.read");
        return view(principal.requireBrewery(), id);
    }

    @PostMapping
    ResponseEntity<QualityViews.NonConformityView> open(
            @Valid @RequestBody QualityDtos.OpenNonConformity body,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("quality.nc.manage");
        var brewery = principal.requireBrewery();
        var id = open.handle(new NonConformityCommands.Open.Command(principal.userId(), brewery,
                body.code(), body.title(), body.description(), body.source(), body.deviationId(),
                body.batchId(), body.severity(), body.containmentDueOn(), body.investigationDueOn(),
                body.verificationDueOn()));
        return ResponseEntity.created(URI.create("/api/v1/quality/non-conformities/" + id))
                .body(view(brewery, id));
    }

    @PostMapping("/{id}/containment")
    QualityViews.NonConformityView contain(@PathVariable UUID id,
            @Valid @RequestBody QualityDtos.Contain body,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("quality.nc.manage");
        var brewery = principal.requireBrewery();
        contain.handle(new NonConformityCommands.Contain.Command(principal.userId(), brewery, id,
                body.description()));
        return view(brewery, id);
    }

    @PostMapping("/{id}/investigation")
    QualityViews.NonConformityView investigate(@PathVariable UUID id,
            @Valid @RequestBody QualityDtos.Investigate body,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("quality.nc.manage");
        var brewery = principal.requireBrewery();
        investigate.handle(new NonConformityCommands.Investigate.Command(principal.userId(), brewery, id,
                body.rootCause(), body.method()));
        return view(brewery, id);
    }

    @PostMapping("/{id}/actions")
    ResponseEntity<QualityViews.NonConformityView> planAction(@PathVariable UUID id,
            @Valid @RequestBody QualityDtos.PlanAction body,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("quality.nc.manage");
        var brewery = principal.requireBrewery();
        planAction.handle(new NonConformityCommands.PlanAction.Command(principal.userId(), brewery, id,
                body.kind(), body.description(), body.owner(), body.dueOn()));
        return ResponseEntity.created(URI.create("/api/v1/quality/non-conformities/" + id))
                .body(view(brewery, id));
    }

    @PostMapping("/{id}/actions/{actionId}/complete")
    QualityViews.NonConformityView completeAction(@PathVariable UUID id, @PathVariable UUID actionId,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("quality.nc.manage");
        var brewery = principal.requireBrewery();
        completeAction.handle(new NonConformityCommands.CompleteAction.Command(principal.userId(), brewery,
                id, actionId));
        return view(brewery, id);
    }

    /** Verificação ineficaz devolve à fase de ação em vez de permitir encerrar com ressalva. */
    @PostMapping("/{id}/verification")
    QualityViews.NonConformityView verify(@PathVariable UUID id,
            @Valid @RequestBody QualityDtos.Verify body,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("quality.nc.manage");
        var brewery = principal.requireBrewery();
        verify.handle(new NonConformityCommands.Verify.Command(principal.userId(), brewery, id,
                body.effective(), body.evidence()));
        return view(brewery, id);
    }

    @PostMapping("/{id}/close")
    QualityViews.NonConformityView close(@PathVariable UUID id,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("quality.nc.close");
        var brewery = principal.requireBrewery();
        close.handle(new NonConformityCommands.Close.Command(principal.userId(), brewery, id));
        return view(brewery, id);
    }

    private QualityViews.NonConformityView view(UUID brewery, UUID id) {
        return queries.nonConformity(brewery, id)
                .map(nc -> QualityViews.NonConformityView.from(nc, today()))
                .orElseThrow(() -> new IllegalArgumentException("não conformidade inexistente"));
    }
}
