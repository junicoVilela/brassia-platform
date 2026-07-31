package br.com.brew.brassia.fermentation.adapter.inbound.web;

import br.com.brew.brassia.fermentation.adapter.inbound.web.dto.FgStabilityView;
import br.com.brew.brassia.fermentation.application.port.inbound.EvaluateFgStabilityUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Estabilidade de FG (FER-003). É consulta: devolve um parecer explicável e não encerra a
 * fermentação — por isso GET, sem auditoria de comando.
 */
@RestController
final class FgStabilityController {

    private final EvaluateFgStabilityUseCase evaluate;

    FgStabilityController(EvaluateFgStabilityUseCase evaluate) {
        this.evaluate = evaluate;
    }

    @GetMapping("/api/v1/fermentation/batches/{batchId}/fg-stability")
    FgStabilityView evaluate(@PathVariable UUID batchId, @RequestParam UUID profileId,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("fermentation.reading.read");
        return FgStabilityView.from(evaluate.handle(principal.requireBrewery(), batchId, profileId));
    }
}
