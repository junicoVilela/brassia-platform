package br.com.brew.brassia.metrology.adapter.inbound.web;

import br.com.brew.brassia.metrology.application.port.inbound.CalibrationPolicyUseCase;
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

/** Periodicidade de calibração por tipo de instrumento (PRM-001). */
@RestController
@RequestMapping("/api/v1/metrology/calibration-policy")
final class CalibrationPolicyController {

    private final CalibrationPolicyUseCase policy;

    CalibrationPolicyController(CalibrationPolicyUseCase policy) {
        this.policy = policy;
    }

    @GetMapping
    View get(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("metrology.instrument.read");
        return View.from(policy.get(principal.requireBrewery()).monthsByType());
    }

    @PutMapping
    View replace(@RequestBody Request body, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("metrology.policy.manage");
        return View.from(policy.replace(principal.userId(), principal.requireBrewery(),
                body.monthsByType()).monthsByType());
    }

    /** Substitui a política inteira: tipo fora do mapa deixa de ter periodicidade. */
    record Request(@NotNull Map<String, Integer> monthsByType) {}

    record View(Map<String, Integer> monthsByType) {

        static View from(Map<br.com.brew.brassia.metrology.domain.InstrumentType, Integer> months) {
            return new View(months.entrySet().stream()
                    .collect(Collectors.toMap(e -> e.getKey().name(), Map.Entry::getValue)));
        }
    }
}
