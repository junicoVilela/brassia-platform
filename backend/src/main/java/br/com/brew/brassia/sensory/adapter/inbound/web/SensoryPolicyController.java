package br.com.brew.brassia.sensory.adapter.inbound.web;

import br.com.brew.brassia.sensory.application.port.inbound.SensoryPolicyUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Escala da ficha sensorial (PRM-001).
 *
 * <p>A resposta diz em voz alta que a mudança não afeta sessões existentes — é a pergunta que
 * qualquer um faz ao mexer numa escala.
 */
@RestController
@RequestMapping("/api/v1/sensory/policy")
final class SensoryPolicyController {

    private final SensoryPolicyUseCase policy;

    SensoryPolicyController(SensoryPolicyUseCase policy) {
        this.policy = policy;
    }

    @GetMapping
    View get(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("sensory.session.read");
        return new View(policy.get(principal.requireBrewery()).maxScore(), true);
    }

    @PutMapping
    View update(@RequestBody Request body, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("sensory.policy.manage");
        return new View(policy.update(principal.userId(), principal.requireBrewery(), body.maxScore())
                .maxScore(), true);
    }

    record Request(@NotNull @Min(3) @Max(100) Integer maxScore) {}

    /** @param appliesToNewSessionsOnly sempre verdadeiro: a escala é congelada em cada sessão */
    record View(int maxScore, boolean appliesToNewSessionsOnly) {}
}
