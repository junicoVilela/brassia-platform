package br.com.brew.brassia.water.adapter.inbound.web;

import br.com.brew.brassia.shared.security.SecurityPrincipal;
import br.com.brew.brassia.shared.web.PageResponse;
import br.com.brew.brassia.water.adapter.inbound.web.dto.RegisterWaterProfileRequest;
import br.com.brew.brassia.water.adapter.inbound.web.dto.UpdateWaterProfileRequest;
import br.com.brew.brassia.water.adapter.inbound.web.dto.WaterProfileResponse;
import br.com.brew.brassia.water.application.port.inbound.ListWaterProfilesUseCase;
import br.com.brew.brassia.water.application.port.inbound.RegisterWaterProfileUseCase;
import br.com.brew.brassia.water.application.port.inbound.UpdateWaterProfileUseCase;
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
@RequestMapping("/api/v1/water/profiles")
final class WaterProfileController {
    private final RegisterWaterProfileUseCase register;
    private final UpdateWaterProfileUseCase update;
    private final ListWaterProfilesUseCase list;

    WaterProfileController(RegisterWaterProfileUseCase register, UpdateWaterProfileUseCase update,
            ListWaterProfilesUseCase list) {
        this.register = register;
        this.update = update;
        this.list = list;
    }

    @GetMapping
    PageResponse<WaterProfileResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("water.read");
        var result = list.handle(new ListWaterProfilesUseCase.Query(principal.requireBrewery(), page, size));
        var content = result.content().stream().map(WaterProfileResponse::from).toList();
        return new PageResponse<>(content, result.page(), result.size(), result.totalElements(), result.totalPages());
    }

    @PostMapping
    ResponseEntity<WaterProfileResponse> create(
            @Valid @RequestBody RegisterWaterProfileRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("water.manage");
        var result = register.handle(new RegisterWaterProfileUseCase.Command(
                principal.userId(), principal.requireBrewery(), request.code(), request.name(),
                request.calcium(), request.magnesium(), request.sodium(), request.sulfate(),
                request.chloride(), request.bicarbonate()));
        return ResponseEntity.created(URI.create("/api/v1/water/profiles/" + result.id()))
                .body(WaterProfileResponse.from(result));
    }

    @PutMapping("/{id}")
    WaterProfileResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateWaterProfileRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("water.manage");
        var result = update.handle(new UpdateWaterProfileUseCase.Command(
                principal.userId(), principal.requireBrewery(), id, request.name(), request.calcium(),
                request.magnesium(), request.sodium(), request.sulfate(), request.chloride(),
                request.bicarbonate(), request.version()));
        return WaterProfileResponse.from(result);
    }
}
