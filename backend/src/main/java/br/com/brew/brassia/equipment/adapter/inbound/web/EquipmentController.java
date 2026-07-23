package br.com.brew.brassia.equipment.adapter.inbound.web;

import br.com.brew.brassia.equipment.adapter.inbound.web.dto.EquipmentResponse;
import br.com.brew.brassia.equipment.adapter.inbound.web.dto.EquipmentRevisionResponse;
import br.com.brew.brassia.equipment.adapter.inbound.web.dto.RegisterEquipmentRequest;
import br.com.brew.brassia.equipment.adapter.inbound.web.dto.UpdateEquipmentRequest;
import br.com.brew.brassia.equipment.application.port.inbound.GetEquipmentRevisionUseCase;
import br.com.brew.brassia.equipment.application.port.inbound.ListEquipmentUseCase;
import br.com.brew.brassia.equipment.application.port.inbound.RegisterEquipmentUseCase;
import br.com.brew.brassia.equipment.application.port.inbound.UpdateEquipmentUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import br.com.brew.brassia.shared.web.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/equipment")
final class EquipmentController {
    private final RegisterEquipmentUseCase register;
    private final UpdateEquipmentUseCase update;
    private final ListEquipmentUseCase list;
    private final GetEquipmentRevisionUseCase getRevision;

    EquipmentController(RegisterEquipmentUseCase register, UpdateEquipmentUseCase update,
            ListEquipmentUseCase list, GetEquipmentRevisionUseCase getRevision) {
        this.register = register;
        this.update = update;
        this.list = list;
        this.getRevision = getRevision;
    }

    @GetMapping
    PageResponse<EquipmentResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("equipment.read");
        var result = list.handle(new ListEquipmentUseCase.Query(principal.requireBrewery(), page, size));
        var content = result.content().stream().map(EquipmentResponse::from).toList();
        return new PageResponse<>(content, result.page(), result.size(), result.totalElements(), result.totalPages());
    }

    @PostMapping
    ResponseEntity<EquipmentResponse> create(
            @Valid @RequestBody RegisterEquipmentRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("equipment.manage");
        var result = register.handle(new RegisterEquipmentUseCase.Command(
                principal.userId(), principal.requireBrewery(), request.code(), request.name(),
                request.capacityLiters(), request.deadSpaceLiters(), request.mashEfficiencyPercent(),
                request.boilOffLitersPerHour()));
        return ResponseEntity.created(URI.create("/api/v1/equipment/" + result.id()))
                .body(EquipmentResponse.from(result));
    }

    @PutMapping("/{id}")
    EquipmentResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEquipmentRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("equipment.manage");
        var result = update.handle(new UpdateEquipmentUseCase.Command(
                principal.userId(), principal.requireBrewery(), id, request.name(), request.capacityLiters(),
                request.deadSpaceLiters(), request.mashEfficiencyPercent(), request.boilOffLitersPerHour(),
                request.version()));
        return EquipmentResponse.from(result);
    }

    @GetMapping("/{id}/revisions/{version}")
    EquipmentRevisionResponse revision(
            @PathVariable UUID id,
            @PathVariable long version,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("equipment.read");
        var result = getRevision.handle(new GetEquipmentRevisionUseCase.Query(
                principal.requireBrewery(), id, version));
        return EquipmentRevisionResponse.from(result);
    }
}
