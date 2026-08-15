package br.com.brew.brassia.costing.adapter.inbound.web;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.costing.application.port.outbound.LaborRateRepository;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * O custo da hora de trabalho da casa (CST-001-A).
 *
 * <p>Uma taxa por cervejaria, e não uma por pessoa: custo de mão de obra por lote é custo médio da hora
 * produtiva. Uma taxa por pessoa faria o mesmo lote sair mais caro na semana em que o cervejeiro sênior
 * trabalhou — o que descreve a escala, não o produto.
 */
@RestController
@RequestMapping("/api/v1/costing/labor-rate")
final class LaborRateController {

    private final LaborRateRepository rates;
    private final AuditTrail audit;

    LaborRateController(LaborRateRepository rates, AuditTrail audit) {
        this.rates = Objects.requireNonNull(rates);
        this.audit = Objects.requireNonNull(audit);
    }

    @GetMapping
    LaborRateView get(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("costing.cost.read");
        // Nulo é resposta legítima: a cervejaria que nunca definiu a taxa não tem mão de obra no custo, e
        // o custeio diz isso como lacuna em vez de somar zero.
        return new LaborRateView(rates.find(principal.requireBrewery()).orElse(null));
    }

    @PutMapping
    LaborRateView save(@AuthenticationPrincipal SecurityPrincipal principal,
            @Valid @RequestBody SaveLaborRateRequest request) {
        principal.requirePermission("costing.labor-rate.manage");
        var brewery = principal.requireBrewery();
        rates.save(brewery, request.costPerHour(), principal.userId());
        audit.record(AuditEvent.success(brewery, principal.userId(), "costing.labor-rate.save",
                "costing.labor_rate", brewery.toString(),
                Map.of("costPerHour", request.costPerHour().toPlainString())));
        return new LaborRateView(request.costPerHour());
    }

    record SaveLaborRateRequest(@NotNull @DecimalMin(value = "0.0", inclusive = false)
            BigDecimal costPerHour) {}

    record LaborRateView(BigDecimal costPerHour) {}
}
