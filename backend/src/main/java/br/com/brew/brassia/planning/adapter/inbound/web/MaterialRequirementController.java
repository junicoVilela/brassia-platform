package br.com.brew.brassia.planning.adapter.inbound.web;

import br.com.brew.brassia.planning.adapter.inbound.web.dto.MaterialLineView;
import br.com.brew.brassia.planning.adapter.inbound.web.dto.MaterialRequirementRequest;
import br.com.brew.brassia.planning.application.port.inbound.MaterialRequirementUseCase;
import br.com.brew.brassia.planning.application.port.inbound.ScheduleMaterialsUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Necessidade de materiais (PLN-002): ad-hoc por receita+volume e por entrada da agenda. */
@RestController
@RequestMapping("/api/v1/planning")
final class MaterialRequirementController {

    private final MaterialRequirementUseCase requirement;
    private final ScheduleMaterialsUseCase scheduleMaterials;

    MaterialRequirementController(MaterialRequirementUseCase requirement,
            ScheduleMaterialsUseCase scheduleMaterials) {
        this.requirement = requirement;
        this.scheduleMaterials = scheduleMaterials;
    }

    @PostMapping("/material-requirement")
    List<MaterialLineView> compute(
            @Valid @RequestBody MaterialRequirementRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("planning.schedule.read");
        return requirement.handle(new MaterialRequirementUseCase.Query(
                        principal.requireBrewery(), request.recipeId(), request.volumeLiters(),
                        request.lossPercent()))
                .stream().map(MaterialLineView::from).toList();
    }

    @GetMapping("/schedule/{id}/materials")
    List<MaterialLineView> forScheduleEntry(
            @PathVariable UUID id,
            @RequestParam(required = false) BigDecimal lossPercent,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("planning.schedule.read");
        return scheduleMaterials.handle(new ScheduleMaterialsUseCase.Query(
                        principal.requireBrewery(), id, lossPercent))
                .stream().map(MaterialLineView::from).toList();
    }
}
