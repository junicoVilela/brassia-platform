package br.com.brew.brassia.sanitation.adapter.inbound.web;

import br.com.brew.brassia.sanitation.adapter.inbound.web.dto.CycleView;
import br.com.brew.brassia.sanitation.adapter.inbound.web.dto.InterruptCycleRequest;
import br.com.brew.brassia.sanitation.adapter.inbound.web.dto.RecordStepRequest;
import br.com.brew.brassia.sanitation.adapter.inbound.web.dto.StartCycleRequest;
import br.com.brew.brassia.sanitation.adapter.inbound.web.dto.VerificationRequest;
import br.com.brew.brassia.sanitation.application.port.inbound.CompleteCycleUseCase;
import br.com.brew.brassia.sanitation.application.port.inbound.GetCycleUseCase;
import br.com.brew.brassia.sanitation.application.port.inbound.InterruptCycleUseCase;
import br.com.brew.brassia.sanitation.application.port.inbound.ListCyclesUseCase;
import br.com.brew.brassia.sanitation.application.port.inbound.RecordStepUseCase;
import br.com.brew.brassia.sanitation.application.port.inbound.RecordVerificationUseCase;
import br.com.brew.brassia.sanitation.application.port.inbound.RejectCycleUseCase;
import br.com.brew.brassia.sanitation.application.port.inbound.ReleaseCycleUseCase;
import br.com.brew.brassia.sanitation.application.port.inbound.ResumeCycleUseCase;
import br.com.brew.brassia.sanitation.application.port.inbound.StartCycleUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
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

/** Execução de ciclos de limpeza/sanitização (CLN-003). */
@RestController
@RequestMapping("/api/v1/sanitation/cycles")
final class CleaningCycleController {

    private final StartCycleUseCase start;
    private final RecordStepUseCase recordStep;
    private final InterruptCycleUseCase interrupt;
    private final ResumeCycleUseCase resume;
    private final CompleteCycleUseCase complete;
    private final RecordVerificationUseCase verify;
    private final ReleaseCycleUseCase release;
    private final RejectCycleUseCase reject;
    private final GetCycleUseCase get;
    private final ListCyclesUseCase list;

    CleaningCycleController(StartCycleUseCase start, RecordStepUseCase recordStep, InterruptCycleUseCase interrupt,
            ResumeCycleUseCase resume, CompleteCycleUseCase complete, RecordVerificationUseCase verify,
            ReleaseCycleUseCase release, RejectCycleUseCase reject, GetCycleUseCase get, ListCyclesUseCase list) {
        this.start = start;
        this.recordStep = recordStep;
        this.interrupt = interrupt;
        this.resume = resume;
        this.complete = complete;
        this.verify = verify;
        this.release = release;
        this.reject = reject;
        this.get = get;
        this.list = list;
    }

    @GetMapping
    List<CycleView> list(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("sanitation.cycle.read");
        return list.handle(principal.requireBrewery()).stream().map(CycleView::from).toList();
    }

    @GetMapping("/{id}")
    CycleView get(@PathVariable UUID id, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("sanitation.cycle.read");
        return CycleView.from(get.handle(principal.requireBrewery(), id));
    }

    @PostMapping
    ResponseEntity<Map<String, Object>> start(
            @Valid @RequestBody StartCycleRequest request, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("sanitation.cycle.execute");
        var id = start.handle(new StartCycleUseCase.Command(
                principal.userId(), principal.requireBrewery(), request.procedureCode(), request.equipmentId()));
        return ResponseEntity.created(URI.create("/api/v1/sanitation/cycles/" + id)).body(Map.of("id", id));
    }

    @PostMapping("/{id}/steps")
    ResponseEntity<Void> recordStep(@PathVariable UUID id, @Valid @RequestBody RecordStepRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("sanitation.cycle.execute");
        if (request.overrideRequested()) {
            // Forçar registro de parâmetro fora da ficha exige alçada.
            principal.requirePermission("sanitation.cycle.override");
        }
        recordStep.handle(new RecordStepUseCase.Command(
                principal.userId(), principal.requireBrewery(), id, request.sequence(),
                request.measuredConcentrationPct(), request.measuredTempC(), request.measuredTimeMinutes(),
                request.flow(), request.evidence(), request.outOfOrderReason(), request.overrideRequested(),
                request.overrideReason()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/interrupt")
    ResponseEntity<Void> interrupt(@PathVariable UUID id, @Valid @RequestBody InterruptCycleRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("sanitation.cycle.execute");
        interrupt.handle(new InterruptCycleUseCase.Command(
                principal.userId(), principal.requireBrewery(), id, request.reason()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/resume")
    ResponseEntity<Void> resume(@PathVariable UUID id, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("sanitation.cycle.execute");
        resume.handle(new ResumeCycleUseCase.Command(principal.userId(), principal.requireBrewery(), id));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/complete")
    ResponseEntity<Void> complete(@PathVariable UUID id, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("sanitation.cycle.execute");
        complete.handle(new CompleteCycleUseCase.Command(principal.userId(), principal.requireBrewery(), id));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/verification")
    ResponseEntity<Void> verify(@PathVariable UUID id, @Valid @RequestBody VerificationRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("sanitation.cycle.execute");
        verify.handle(new RecordVerificationUseCase.Command(
                principal.userId(), principal.requireBrewery(), id, request.rinsePassed(), request.visualPassed(),
                request.atpRlu(), request.atpThreshold(), request.microPassed()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/release")
    ResponseEntity<Void> release(@PathVariable UUID id, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("sanitation.cycle.release");
        release.handle(new ReleaseCycleUseCase.Command(principal.userId(), principal.requireBrewery(), id));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reject")
    ResponseEntity<Void> reject(@PathVariable UUID id, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("sanitation.cycle.release");
        reject.handle(new RejectCycleUseCase.Command(principal.userId(), principal.requireBrewery(), id));
        return ResponseEntity.noContent().build();
    }
}
