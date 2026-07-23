package br.com.brew.brassia.water.adapter.inbound.web;

import br.com.brew.brassia.shared.security.SecurityPrincipal;
import br.com.brew.brassia.shared.web.PageResponse;
import br.com.brew.brassia.water.adapter.inbound.web.dto.RegisterWaterSourceRequest;
import br.com.brew.brassia.water.adapter.inbound.web.dto.UpdateWaterSourceRequest;
import br.com.brew.brassia.water.adapter.inbound.web.dto.WaterSourceResponse;
import br.com.brew.brassia.water.application.port.inbound.ListWaterSourcesUseCase;
import br.com.brew.brassia.water.application.port.inbound.RegisterWaterSourceUseCase;
import br.com.brew.brassia.water.application.port.inbound.UpdateWaterSourceUseCase;
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
@RequestMapping("/api/v1/water/sources")
final class WaterSourceController {
    private final RegisterWaterSourceUseCase register;
    private final UpdateWaterSourceUseCase update;
    private final ListWaterSourcesUseCase list;

    WaterSourceController(RegisterWaterSourceUseCase register, UpdateWaterSourceUseCase update,
            ListWaterSourcesUseCase list) {
        this.register = register;
        this.update = update;
        this.list = list;
    }

    @GetMapping
    PageResponse<WaterSourceResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("water.read");
        var result = list.handle(new ListWaterSourcesUseCase.Query(principal.requireBrewery(), page, size));
        var content = result.content().stream().map(WaterSourceResponse::from).toList();
        return new PageResponse<>(content, result.page(), result.size(), result.totalElements(), result.totalPages());
    }

    @PostMapping
    ResponseEntity<WaterSourceResponse> create(
            @Valid @RequestBody RegisterWaterSourceRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("water.manage");
        var result = register.handle(new RegisterWaterSourceUseCase.Command(
                principal.userId(), principal.requireBrewery(), request.code(), request.name()));
        return ResponseEntity.created(URI.create("/api/v1/water/sources/" + result.id()))
                .body(WaterSourceResponse.from(result));
    }

    @PutMapping("/{id}")
    WaterSourceResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateWaterSourceRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("water.manage");
        var result = update.handle(new UpdateWaterSourceUseCase.Command(
                principal.userId(), principal.requireBrewery(), id, request.name(), request.version()));
        return WaterSourceResponse.from(result);
    }
}
