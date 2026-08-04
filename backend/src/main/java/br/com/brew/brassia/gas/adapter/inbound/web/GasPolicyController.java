package br.com.brew.brassia.gas.adapter.inbound.web;

import br.com.brew.brassia.gas.application.port.inbound.GasPolicyUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Política de gases (PRM-001): periodicidade de requalificação de cilindro. */
@RestController
@RequestMapping("/api/v1/gas/policy")
final class GasPolicyController {

    private final GasPolicyUseCase policy;

    GasPolicyController(GasPolicyUseCase policy) {
        this.policy = policy;
    }

    @GetMapping
    View get(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("gas.read");
        return View.from(policy.get(principal.requireBrewery()).requalificationMonths().orElse(null));
    }

    @PutMapping
    View update(@RequestBody Request body, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("gas.policy.manage");
        return View.from(policy.update(principal.userId(), principal.requireBrewery(),
                body.requalificationMonths()).requalificationMonths().orElse(null));
    }

    record Request(@Min(1) @Max(240) Integer requalificationMonths) {}

    record View(Integer requalificationMonths, boolean derivesDueDate) {

        static View from(Integer months) {
            return new View(months, months != null);
        }
    }
}
