package br.com.brew.brassia.fermentation.adapter.inbound.web;

import br.com.brew.brassia.fermentation.adapter.inbound.web.dto.ScheduleDtos.AddStepRequest;
import br.com.brew.brassia.fermentation.adapter.inbound.web.dto.ScheduleDtos.ExecuteStepRequest;
import br.com.brew.brassia.fermentation.adapter.inbound.web.dto.ScheduleDtos.PlanScheduleRequest;
import br.com.brew.brassia.fermentation.adapter.inbound.web.dto.ScheduleDtos.ReschedulePreviewView;
import br.com.brew.brassia.fermentation.adapter.inbound.web.dto.ScheduleDtos.RescheduleRequest;
import br.com.brew.brassia.fermentation.adapter.inbound.web.dto.ScheduleDtos.ScheduleView;
import br.com.brew.brassia.fermentation.application.port.inbound.AddScheduleStepUseCase;
import br.com.brew.brassia.fermentation.application.port.inbound.ExecuteScheduleStepUseCase;
import br.com.brew.brassia.fermentation.application.port.inbound.GetScheduleUseCase;
import br.com.brew.brassia.fermentation.application.port.inbound.PlanScheduleUseCase;
import br.com.brew.brassia.fermentation.application.port.inbound.RescheduleStepUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Linha do tempo e agenda de fermentação do lote (FER-004). */
@RestController
@RequestMapping("/api/v1/fermentation/batches/{batchId}/schedule")
final class FermentationScheduleController {

    private final PlanScheduleUseCase plan;
    private final GetScheduleUseCase get;
    private final AddScheduleStepUseCase addStep;
    private final RescheduleStepUseCase reschedule;
    private final ExecuteScheduleStepUseCase execute;

    FermentationScheduleController(PlanScheduleUseCase plan, GetScheduleUseCase get, AddScheduleStepUseCase addStep,
            RescheduleStepUseCase reschedule, ExecuteScheduleStepUseCase execute) {
        this.plan = plan;
        this.get = get;
        this.addStep = addStep;
        this.reschedule = reschedule;
        this.execute = execute;
    }

    @GetMapping
    ScheduleView get(@PathVariable UUID batchId, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("fermentation.schedule.read");
        return ScheduleView.from(get.handle(principal.requireBrewery(), batchId));
    }

    @PostMapping
    ResponseEntity<Map<String, Object>> plan(@PathVariable UUID batchId,
            @Valid @RequestBody PlanScheduleRequest request, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("fermentation.schedule.manage");
        var result = plan.handle(new PlanScheduleUseCase.Command(principal.userId(), principal.requireBrewery(),
                batchId, request.profileId(), request.start(), request.responsibleUserId(),
                request.defaultDurationDays(), request.toleranceHours()));
        return ResponseEntity
                .created(URI.create("/api/v1/fermentation/batches/" + batchId + "/schedule"))
                .body(Map.of("id", result.id(), "steps", result.steps()));
    }

    @PostMapping("/steps")
    ResponseEntity<Map<String, Object>> addStep(@PathVariable UUID batchId,
            @Valid @RequestBody AddStepRequest request, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("fermentation.schedule.manage");
        var id = addStep.handle(new AddScheduleStepUseCase.Command(principal.userId(), principal.requireBrewery(),
                batchId, request.name(), request.action(), request.condition(), request.conditionDays(),
                request.targetGravity(), request.plannedStart(), request.plannedEnd(), request.toleranceHours(),
                request.responsibleUserId(), request.dependsOnPrevious()));
        return ResponseEntity.created(URI.create("/api/v1/fermentation/batches/" + batchId + "/schedule"))
                .body(Map.of("id", id));
    }

    /**
     * Move a data de uma etapa. Com {@code apply=false} devolve só a prévia — nada é gravado
     * antes de o cervejeiro ver o que muda.
     */
    @PostMapping("/steps/{stepId}/reschedule")
    ReschedulePreviewView reschedule(@PathVariable UUID batchId, @PathVariable UUID stepId,
            @Valid @RequestBody RescheduleRequest request, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission(request.apply() ? "fermentation.schedule.manage" : "fermentation.schedule.read");
        return ReschedulePreviewView.from(reschedule.handle(new RescheduleStepUseCase.Command(principal.userId(),
                principal.requireBrewery(), batchId, stepId, request.newStart(), request.apply())));
    }

    @PostMapping("/steps/{stepId}/execute")
    void execute(@PathVariable UUID batchId, @PathVariable UUID stepId,
            @Valid @RequestBody ExecuteStepRequest request, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("fermentation.schedule.manage");
        execute.handle(new ExecuteScheduleStepUseCase.Command(principal.userId(), principal.requireBrewery(),
                batchId, stepId, request.executedAt(), request.justification()));
    }
}
