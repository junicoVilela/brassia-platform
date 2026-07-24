package br.com.brew.brassia.water.adapter.inbound.web;

import br.com.brew.brassia.water.adapter.inbound.web.dto.ChargeBalanceRequest;
import br.com.brew.brassia.water.adapter.inbound.web.dto.CreateWaterReferenceProfileRequest;
import br.com.brew.brassia.water.adapter.inbound.web.dto.WaterReferenceProfileActionResponse;
import br.com.brew.brassia.water.adapter.inbound.web.dto.WaterReferenceProfileResponse;
import br.com.brew.brassia.water.application.port.inbound.ChargeBalanceUseCase;
import br.com.brew.brassia.water.application.port.inbound.CreateWaterReferenceProfileUseCase;
import br.com.brew.brassia.water.application.port.inbound.ListWaterReferenceProfilesUseCase;
import br.com.brew.brassia.water.application.port.inbound.PublishWaterReferenceProfileUseCase;
import br.com.brew.brassia.water.domain.ChargeBalance;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/water")
final class WaterReferenceProfileController {

    private final CreateWaterReferenceProfileUseCase create;
    private final ListWaterReferenceProfilesUseCase list;
    private final PublishWaterReferenceProfileUseCase publish;
    private final ChargeBalanceUseCase chargeBalance;

    WaterReferenceProfileController(CreateWaterReferenceProfileUseCase create,
            ListWaterReferenceProfilesUseCase list, PublishWaterReferenceProfileUseCase publish,
            ChargeBalanceUseCase chargeBalance) {
        this.create = create;
        this.list = list;
        this.publish = publish;
        this.chargeBalance = chargeBalance;
    }

    @GetMapping("/reference-profiles")
    PageResponse<WaterReferenceProfileResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("water.read");
        var result = list.handle(new ListWaterReferenceProfilesUseCase.Query(principal.requireBrewery(), page, size));
        var content = result.content().stream().map(WaterReferenceProfileResponse::from).toList();
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) result.total() / size);
        return new PageResponse<>(content, page, size, result.total(), totalPages);
    }

    @PostMapping("/reference-profiles")
    ResponseEntity<WaterReferenceProfileActionResponse> create(
            @Valid @RequestBody CreateWaterReferenceProfileRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("water.manage");
        var result = create.handle(new CreateWaterReferenceProfileUseCase.Command(
                principal.userId(), principal.requireBrewery(), request.name(), request.region(), request.edition(),
                request.toIons(), request.alkalinity(), request.hardness(), request.ph(), request.sourceId(),
                request.sourceName()));
        return ResponseEntity.created(URI.create("/api/v1/water/reference-profiles/" + result.id()))
                .body(new WaterReferenceProfileActionResponse(result.id(), result.status()));
    }

    @PostMapping("/reference-profiles/{id}/publish")
    WaterReferenceProfileActionResponse publish(
            @PathVariable UUID id,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("water.manage");
        var result = publish.handle(new PublishWaterReferenceProfileUseCase.Command(
                principal.userId(), principal.requireBrewery(), id));
        return new WaterReferenceProfileActionResponse(null, result.status());
    }

    @PostMapping("/charge-balance")
    ChargeBalance chargeBalance(
            @Valid @RequestBody ChargeBalanceRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("water.read");
        return chargeBalance.handle(new ChargeBalanceUseCase.Query(request.toIons()));
    }
}
