package br.com.brew.brassia.sanitation.adapter.inbound.web;

import br.com.brew.brassia.sanitation.application.port.inbound.CleaningPolicyUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Política de limpeza da cervejaria (PRM-001): validade da liberação de CIP. */
@RestController
@RequestMapping("/api/v1/sanitation/cleaning-policy")
final class CleaningPolicyController {

    private final CleaningPolicyUseCase policy;

    CleaningPolicyController(CleaningPolicyUseCase policy) {
        this.policy = policy;
    }

    @GetMapping
    View get(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("sanitation.cycle.read");
        return View.from(policy.get(principal.requireBrewery()).validityHours().orElse(null));
    }

    @PutMapping
    View update(@RequestBody Request body, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("sanitation.policy.manage");
        return View.from(policy.update(principal.userId(), principal.requireBrewery(),
                body.validityHours()).validityHours().orElse(null));
    }

    /** @param validityHours nulo remove o prazo; a liberação volta a não expirar por tempo */
    record Request(@Min(1) @Max(8760) Integer validityHours) {}

    record View(Integer validityHours, boolean expiresByTime) {

        static View from(Integer hours) {
            return new View(hours, hours != null);
        }
    }
}
