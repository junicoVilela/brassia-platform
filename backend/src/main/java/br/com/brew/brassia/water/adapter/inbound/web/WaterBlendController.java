package br.com.brew.brassia.water.adapter.inbound.web;

import br.com.brew.brassia.shared.security.SecurityPrincipal;
import br.com.brew.brassia.water.adapter.inbound.web.dto.BlendResultResponse;
import br.com.brew.brassia.water.adapter.inbound.web.dto.SimulateBlendRequest;
import br.com.brew.brassia.water.application.port.inbound.SimulateWaterBlendUseCase;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/water/blends")
final class WaterBlendController {
    private final SimulateWaterBlendUseCase simulate;

    WaterBlendController(SimulateWaterBlendUseCase simulate) {
        this.simulate = simulate;
    }

    @PostMapping("/simulate")
    BlendResultResponse simulate(
            @Valid @RequestBody SimulateBlendRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("water.read");
        var inputs = request.inputs().stream()
                .map(i -> new SimulateWaterBlendUseCase.Input(i.sourceId(), i.volumeLiters()))
                .toList();
        var result = simulate.handle(new SimulateWaterBlendUseCase.Command(
                principal.requireBrewery(), inputs, request.targetProfileId()));
        return BlendResultResponse.from(result);
    }
}
