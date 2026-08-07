package br.com.brew.brassia.reporting.adapter.inbound.web;

import br.com.brew.brassia.reporting.adapter.inbound.web.dto.DashboardDtos;
import br.com.brew.brassia.reporting.application.port.inbound.DashboardQueries;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Painel operacional (RPT-002).
 *
 * <p>Consulta pura, com o período como parâmetro. Cada indicador chega com definição, período e
 * destino de drill-down — não por convenção da tela, mas porque o domínio recusa construir um
 * indicador sem os três.
 */
@RestController
@RequestMapping("/api/v1/reporting")
final class DashboardController {

    /** Trinta dias: janela em que uma cervejaria pequena tem brassagem, envase e apuração. */
    private static final int DEFAULT_DAYS = 30;

    private final DashboardQueries dashboard;

    DashboardController(DashboardQueries dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/dashboard")
    DashboardDtos.DashboardView dashboard(@AuthenticationPrincipal SecurityPrincipal principal,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to) {
        principal.requirePermission("reporting.dashboard.read");
        var end = to == null ? Instant.now() : to;
        var start = from == null ? end.minus(DEFAULT_DAYS, ChronoUnit.DAYS) : from;
        return DashboardDtos.DashboardView.from(
                dashboard.dashboard(principal.requireBrewery(), start, end));
    }
}
