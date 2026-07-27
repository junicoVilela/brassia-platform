package br.com.brew.brassia.sanitation.adapter.inbound.web;

import br.com.brew.brassia.sanitation.adapter.inbound.web.dto.CreateProcedureRequest;
import br.com.brew.brassia.sanitation.adapter.inbound.web.dto.ProcedureStepDto;
import br.com.brew.brassia.sanitation.adapter.inbound.web.dto.ProcedureView;
import br.com.brew.brassia.sanitation.adapter.inbound.web.dto.UpdateProcedureRequest;
import br.com.brew.brassia.sanitation.application.port.inbound.CreateProcedureUseCase;
import br.com.brew.brassia.sanitation.application.port.inbound.GetProcedureUseCase;
import br.com.brew.brassia.sanitation.application.port.inbound.ListProceduresUseCase;
import br.com.brew.brassia.sanitation.application.port.inbound.PublishProcedureUseCase;
import br.com.brew.brassia.sanitation.application.port.inbound.UpdateProcedureUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** POPs de limpeza/sanitização versionados (CLN-001). */
@RestController
@RequestMapping("/api/v1/sanitation/procedures")
final class ProcedureController {

    private final CreateProcedureUseCase createProcedure;
    private final UpdateProcedureUseCase updateProcedure;
    private final PublishProcedureUseCase publishProcedure;
    private final ListProceduresUseCase listProcedures;
    private final GetProcedureUseCase getProcedure;

    ProcedureController(CreateProcedureUseCase createProcedure, UpdateProcedureUseCase updateProcedure,
            PublishProcedureUseCase publishProcedure, ListProceduresUseCase listProcedures,
            GetProcedureUseCase getProcedure) {
        this.createProcedure = createProcedure;
        this.updateProcedure = updateProcedure;
        this.publishProcedure = publishProcedure;
        this.listProcedures = listProcedures;
        this.getProcedure = getProcedure;
    }

    @GetMapping
    List<ProcedureView> list(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("sanitation.procedure.read");
        return listProcedures.handle(principal.requireBrewery()).stream().map(ProcedureView::from).toList();
    }

    @GetMapping("/{id}")
    ProcedureView get(@PathVariable UUID id, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("sanitation.procedure.read");
        return ProcedureView.from(getProcedure.handle(principal.requireBrewery(), id));
    }

    @PostMapping
    ResponseEntity<Created> create(@Valid @RequestBody CreateProcedureRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("sanitation.procedure.manage");
        var result = createProcedure.handle(new CreateProcedureUseCase.Command(
                principal.userId(), principal.requireBrewery(), request.code(), request.name(),
                request.steps().stream().map(ProcedureStepDto::toInput).toList()));
        return ResponseEntity.created(URI.create("/api/v1/sanitation/procedures/" + result.id()))
                .body(new Created(result.id(), result.version()));
    }

    record Created(UUID id, int version) {}

    @PutMapping("/{id}")
    void update(@PathVariable UUID id, @Valid @RequestBody UpdateProcedureRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("sanitation.procedure.manage");
        updateProcedure.handle(new UpdateProcedureUseCase.Command(
                principal.userId(), principal.requireBrewery(), id, request.name(),
                request.steps().stream().map(ProcedureStepDto::toInput).toList()));
    }

    @PostMapping("/{id}/publish")
    void publish(@PathVariable UUID id, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("sanitation.procedure.manage");
        publishProcedure.handle(new PublishProcedureUseCase.Command(
                principal.userId(), principal.requireBrewery(), id));
    }
}
