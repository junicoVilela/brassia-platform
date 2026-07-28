package br.com.brew.brassia.sanitation.adapter.inbound.web;

import br.com.brew.brassia.sanitation.adapter.inbound.web.dto.ConsumptionSummaryView;
import br.com.brew.brassia.sanitation.application.port.inbound.ConsumptionSummaryUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Comparação consultiva de consumo por POP (CLN-005). Read-only — não altera nem reduz
 * parâmetros do POP; reduzir limite exige uma nova versão publicada (CLN-001).
 */
@RestController
@RequestMapping("/api/v1/sanitation/consumption")
final class ConsumptionController {

    private final ConsumptionSummaryUseCase summary;

    ConsumptionController(ConsumptionSummaryUseCase summary) {
        this.summary = summary;
    }

    @GetMapping("/summary")
    ConsumptionSummaryView summary(@RequestParam String procedureCode,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("sanitation.consumption.read");
        return ConsumptionSummaryView.from(summary.handle(principal.requireBrewery(), procedureCode));
    }
}
