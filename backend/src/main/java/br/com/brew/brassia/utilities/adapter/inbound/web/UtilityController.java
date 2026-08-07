package br.com.brew.brassia.utilities.adapter.inbound.web;

import br.com.brew.brassia.shared.security.SecurityPrincipal;
import br.com.brew.brassia.utilities.adapter.inbound.web.dto.UtilityDtos;
import br.com.brew.brassia.utilities.application.port.inbound.UtilityQueries;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Água, energia e CO₂ por litro envasado (UTL-001).
 *
 * <p>Consulta pura, com o período como parâmetro: o mesmo período responde o mesmo enquanto os
 * fatos não mudarem. Um ciclo registrado com atraso muda o número do mês passado — e deve mudar,
 * porque a água foi gasta.
 */
@RestController
@RequestMapping("/api/v1/utilities")
final class UtilityController {

    /** Trinta dias: janela em que uma cervejaria pequena tem ciclos e envases suficientes para comparar. */
    private static final int DEFAULT_DAYS = 30;

    private final UtilityQueries queries;

    UtilityController(UtilityQueries queries) {
        this.queries = queries;
    }

    @GetMapping("/indicators")
    UtilityDtos.ReportView indicators(@AuthenticationPrincipal SecurityPrincipal principal,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to) {
        principal.requirePermission("utilities.indicator.read");
        var end = to == null ? Instant.now() : to;
        var start = from == null ? end.minus(DEFAULT_DAYS, ChronoUnit.DAYS) : from;
        return UtilityDtos.ReportView.from(queries.report(principal.requireBrewery(), start, end));
    }
}
