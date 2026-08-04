package br.com.brew.brassia.quality.adapter.inbound.web;

import br.com.brew.brassia.quality.application.port.inbound.CapaPolicyUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Prazos do CAPA por severidade (PRM-001), em dias corridos da abertura. */
@RestController
@RequestMapping("/api/v1/quality/capa-policy")
final class CapaPolicyController {

    private final CapaPolicyUseCase policy;

    CapaPolicyController(CapaPolicyUseCase policy) {
        this.policy = policy;
    }

    @GetMapping
    View get(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("quality.nc.read");
        return View.from(policy.get(principal.requireBrewery()));
    }

    @PutMapping
    View replace(@RequestBody Request body, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("quality.policy.manage");
        return View.from(policy.replace(principal.userId(), principal.requireBrewery(),
                body.bySeverity().entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey,
                                e -> new CapaPolicyUseCase.Deadlines(e.getValue().containmentDays(),
                                        e.getValue().investigationDays(),
                                        e.getValue().verificationDays())))));
    }

    record DeadlinesDto(int containmentDays, int investigationDays, int verificationDays) {}

    record Request(@NotNull Map<String, DeadlinesDto> bySeverity) {}

    record View(Map<String, DeadlinesDto> bySeverity) {

        static View from(br.com.brew.brassia.quality.domain.CapaPolicy policy) {
            return new View(policy.bySeverity().entrySet().stream()
                    .collect(Collectors.toMap(e -> e.getKey().name(),
                            e -> new DeadlinesDto(e.getValue().containmentDays(),
                                    e.getValue().investigationDays(), e.getValue().verificationDays()))));
        }
    }
}
