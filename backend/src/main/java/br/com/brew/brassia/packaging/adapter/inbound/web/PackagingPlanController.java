package br.com.brew.brassia.packaging.adapter.inbound.web;

import br.com.brew.brassia.packaging.adapter.inbound.web.dto.CancelPackagingPlanRequest;
import br.com.brew.brassia.packaging.adapter.inbound.web.dto.ConfirmChecklistItemRequest;
import br.com.brew.brassia.packaging.adapter.inbound.web.dto.PackagingPlanView;
import br.com.brew.brassia.packaging.adapter.inbound.web.dto.PackagingRunDtos;
import br.com.brew.brassia.packaging.adapter.inbound.web.dto.PlanPackagingRequest;
import br.com.brew.brassia.packaging.application.port.inbound.CancelPackagingPlanUseCase;
import br.com.brew.brassia.packaging.application.port.inbound.ConfirmChecklistItemUseCase;
import br.com.brew.brassia.packaging.application.port.inbound.ExecutePackagingUseCase;
import br.com.brew.brassia.packaging.application.port.inbound.GetPackagingRunUseCase;
import br.com.brew.brassia.packaging.application.port.inbound.PackagingPlanQueries;
import br.com.brew.brassia.packaging.application.port.inbound.PlanPackagingUseCase;
import br.com.brew.brassia.packaging.application.port.inbound.ReservePackagingPlanUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Planos de envase (PKG-001): planejamento, checklist, reserva e cancelamento. */
@RestController
@RequestMapping("/api/v1/packaging/plans")
final class PackagingPlanController {

    private final PlanPackagingUseCase plan;
    private final ConfirmChecklistItemUseCase confirm;
    private final ReservePackagingPlanUseCase reserve;
    private final CancelPackagingPlanUseCase cancel;
    private final ExecutePackagingUseCase execute;
    private final GetPackagingRunUseCase run;
    private final PackagingPlanQueries queries;

    PackagingPlanController(PlanPackagingUseCase plan, ConfirmChecklistItemUseCase confirm,
            ReservePackagingPlanUseCase reserve, CancelPackagingPlanUseCase cancel,
            ExecutePackagingUseCase execute, GetPackagingRunUseCase run, PackagingPlanQueries queries) {
        this.plan = plan;
        this.confirm = confirm;
        this.reserve = reserve;
        this.cancel = cancel;
        this.execute = execute;
        this.run = run;
        this.queries = queries;
    }

    @GetMapping
    List<PackagingPlanView> list(@RequestParam(required = false) UUID batchId,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("packaging.plan.read");
        return queries.list(principal.requireBrewery(), batchId).stream().map(PackagingPlanView::from).toList();
    }

    @GetMapping("/{id}")
    PackagingPlanView get(@PathVariable UUID id, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("packaging.plan.read");
        return queries.find(principal.requireBrewery(), id)
                .map(PackagingPlanView::from)
                .orElseThrow(() -> new IllegalArgumentException("plano de envase inexistente"));
    }

    @PostMapping
    ResponseEntity<Planned> plan(@Valid @RequestBody PlanPackagingRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("packaging.plan.manage");
        var result = plan.handle(new PlanPackagingUseCase.Command(
                principal.userId(), principal.requireBrewery(), request.code(), request.batchId(),
                request.containerId(), request.plannedUnits(), request.lineEquipmentId(), request.plannedStart(),
                request.plannedEnd()));
        return ResponseEntity.created(URI.create("/api/v1/packaging/plans/" + result.id()))
                .body(new Planned(result.id(), result.plannedVolumeLiters()));
    }

    record Planned(UUID id, BigDecimal plannedVolumeLiters) {}

    @PostMapping("/{id}/checklist")
    void confirm(@PathVariable UUID id, @Valid @RequestBody ConfirmChecklistItemRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("packaging.plan.manage");
        confirm.handle(new ConfirmChecklistItemUseCase.Command(
                principal.userId(), principal.requireBrewery(), id, request.item()));
    }

    /** Verifica linha e limpeza e reserva a embalagem; recusa lista todos os bloqueios. */
    @PostMapping("/{id}/reserve")
    ReservePackagingPlanUseCase.Result reserve(@PathVariable UUID id,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("packaging.plan.manage");
        return reserve.handle(new ReservePackagingPlanUseCase.Command(
                principal.userId(), principal.requireBrewery(), id));
    }

    /** Registra o envase executado; a perda é derivada e o balanço fecha por construção. */
    @PostMapping("/{id}/execution")
    ExecutePackagingUseCase.Result execute(@PathVariable UUID id,
            @Valid @RequestBody PackagingRunDtos.ExecutePackagingRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("packaging.plan.manage");
        return execute.handle(new ExecutePackagingUseCase.Command(
                principal.userId(), principal.requireBrewery(), id, request.inputVolumeLiters(),
                request.producedUnits(), request.rejectedUnits(), request.note()));
    }

    @GetMapping("/{id}/execution")
    PackagingRunDtos.PackagingRunView execution(@PathVariable UUID id,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("packaging.plan.read");
        return run.handle(principal.requireBrewery(), id)
                .map(PackagingRunDtos.PackagingRunView::from)
                .orElseThrow(() -> new IllegalArgumentException("plano sem execução registrada"));
    }

    @PostMapping("/{id}/cancel")
    void cancel(@PathVariable UUID id, @Valid @RequestBody CancelPackagingPlanRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("packaging.plan.manage");
        cancel.handle(new CancelPackagingPlanUseCase.Command(
                principal.userId(), principal.requireBrewery(), id, request.reason()));
    }
}
