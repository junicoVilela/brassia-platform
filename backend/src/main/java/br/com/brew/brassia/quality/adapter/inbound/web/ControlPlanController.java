package br.com.brew.brassia.quality.adapter.inbound.web;

import br.com.brew.brassia.quality.adapter.inbound.web.dto.QualityDtos;
import br.com.brew.brassia.quality.adapter.inbound.web.dto.QualityViews;
import br.com.brew.brassia.quality.application.port.inbound.ControlPlanCommands;
import br.com.brew.brassia.quality.application.port.inbound.QualityQueries;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Planos de controle (QLT-001): rascunho, pontos, publicação e nova versão. */
@RestController
@RequestMapping("/api/v1/quality/control-plans")
final class ControlPlanController {

    private final ControlPlanCommands.Create create;
    private final ControlPlanCommands.Amend amend;
    private final ControlPlanCommands.AddPoint addPoint;
    private final ControlPlanCommands.RemovePoint removePoint;
    private final ControlPlanCommands.Publish publish;
    private final ControlPlanCommands.NewVersion newVersion;
    private final QualityQueries queries;

    ControlPlanController(ControlPlanCommands.Create create, ControlPlanCommands.Amend amend,
            ControlPlanCommands.AddPoint addPoint, ControlPlanCommands.RemovePoint removePoint,
            ControlPlanCommands.Publish publish, ControlPlanCommands.NewVersion newVersion,
            QualityQueries queries) {
        this.create = create;
        this.amend = amend;
        this.addPoint = addPoint;
        this.removePoint = removePoint;
        this.publish = publish;
        this.newVersion = newVersion;
        this.queries = queries;
    }

    @GetMapping
    List<QualityViews.PlanView> list(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("quality.plan.read");
        return queries.plans(principal.requireBrewery()).stream().map(QualityViews.PlanView::from).toList();
    }

    @GetMapping("/{id}")
    QualityViews.PlanView get(@PathVariable UUID id, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("quality.plan.read");
        return view(principal.requireBrewery(), id);
    }

    @PostMapping
    ResponseEntity<QualityViews.PlanView> create(@Valid @RequestBody QualityDtos.CreatePlan body,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("quality.plan.manage");
        var brewery = principal.requireBrewery();
        var id = create.handle(new ControlPlanCommands.Create.Command(principal.userId(), brewery,
                body.code(), body.name(), body.recipeId(), body.stage()));
        return ResponseEntity.created(URI.create("/api/v1/quality/control-plans/" + id))
                .body(view(brewery, id));
    }

    @PutMapping("/{id}")
    QualityViews.PlanView amend(@PathVariable UUID id, @Valid @RequestBody QualityDtos.AmendPlan body,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("quality.plan.manage");
        var brewery = principal.requireBrewery();
        amend.handle(new ControlPlanCommands.Amend.Command(principal.userId(), brewery, id, body.name(),
                body.recipeId(), body.stage()));
        return view(brewery, id);
    }

    @PostMapping("/{id}/points")
    ResponseEntity<QualityViews.PlanView> addPoint(@PathVariable UUID id,
            @Valid @RequestBody QualityDtos.AddPoint body,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("quality.plan.manage");
        var brewery = principal.requireBrewery();
        addPoint.handle(new ControlPlanCommands.AddPoint.Command(principal.userId(), brewery, id,
                body.parameter(), body.min(), body.max(), body.target(), body.unit(), body.frequencyKind(),
                body.everyHours(), body.action(), body.severity(), body.critical()));
        return ResponseEntity.created(URI.create("/api/v1/quality/control-plans/" + id))
                .body(view(brewery, id));
    }

    @DeleteMapping("/{id}/points/{pointId}")
    QualityViews.PlanView removePoint(@PathVariable UUID id, @PathVariable UUID pointId,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("quality.plan.manage");
        var brewery = principal.requireBrewery();
        removePoint.handle(new ControlPlanCommands.RemovePoint.Command(principal.userId(), brewery, id,
                pointId));
        return view(brewery, id);
    }

    /** Publicar congela a versão: daqui em diante o plano julga e não muda mais. */
    @PostMapping("/{id}/publish")
    QualityViews.PlanView publish(@PathVariable UUID id,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("quality.plan.manage");
        var brewery = principal.requireBrewery();
        publish.handle(new ControlPlanCommands.Publish.Command(principal.userId(), brewery, id));
        return view(brewery, id);
    }

    @PostMapping("/{id}/new-version")
    ResponseEntity<QualityViews.PlanView> newVersion(@PathVariable UUID id,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("quality.plan.manage");
        var brewery = principal.requireBrewery();
        var draftId = newVersion.handle(new ControlPlanCommands.NewVersion.Command(principal.userId(),
                brewery, id));
        return ResponseEntity.created(URI.create("/api/v1/quality/control-plans/" + draftId))
                .body(view(brewery, draftId));
    }

    @GetMapping("/{id}/measurements")
    List<QualityViews.MeasurementView> measurements(@PathVariable UUID id,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("quality.plan.read");
        return queries.measurements(principal.requireBrewery(), id).stream()
                .map(QualityViews.MeasurementView::from)
                .toList();
    }

    private QualityViews.PlanView view(UUID brewery, UUID id) {
        return queries.plan(brewery, id)
                .map(QualityViews.PlanView::from)
                .orElseThrow(() -> new IllegalArgumentException("plano de controle inexistente"));
    }
}
