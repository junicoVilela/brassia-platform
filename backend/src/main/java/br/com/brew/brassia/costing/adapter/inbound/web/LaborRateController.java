package br.com.brew.brassia.costing.adapter.inbound.web;

import br.com.brew.brassia.shared.money.Money;
import br.com.brew.brassia.brewery.BreweryCurrencyLookup;
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
    private final BreweryCurrencyLookup currencies;
    private final AuditTrail audit;

    LaborRateController(LaborRateRepository rates, BreweryCurrencyLookup currencies, AuditTrail audit) {
        this.rates = Objects.requireNonNull(rates);
        this.currencies = Objects.requireNonNull(currencies);
        this.audit = Objects.requireNonNull(audit);
    }

    @GetMapping
    LaborRateView get(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("costing.cost.read");
        // Nulo é resposta legítima: a cervejaria que nunca definiu a taxa não tem mão de obra no custo, e
        // o custeio diz isso como lacuna em vez de somar zero.
        var rate = rates.find(principal.requireBrewery()).orElse(null);
        return rate == null ? new LaborRateView(null, null)
                : new LaborRateView(rate.toMinorUnit(), rate.currency());
    }

    @PutMapping
    LaborRateView save(@AuthenticationPrincipal SecurityPrincipal principal,
            @Valid @RequestBody SaveLaborRateRequest request) {
        principal.requirePermission("costing.labor-rate.manage");
        var brewery = principal.requireBrewery();
        // A moeda é da casa, e não do formulário: pedi-la aqui deixaria a taxa da hora divergir da moeda
        // em que o custo do lote é somado, e o total misturaria as duas sem que nada reclamasse.
        var rate = new Money(request.costPerHour(), currencies.currencyOf(brewery));
        rates.save(brewery, rate, principal.userId());
        audit.record(AuditEvent.success(brewery, principal.userId(), "costing.labor-rate.save",
                "costing.labor_rate", brewery.toString(),
                Map.of("costPerHour", rate.toString())));
        return new LaborRateView(rate.toMinorUnit(), rate.currency());
    }

    record SaveLaborRateRequest(@NotNull @DecimalMin(value = "0.0", inclusive = false)
            BigDecimal costPerHour) {}

    /** @param currency nulo junto com o valor: quem nunca definiu a taxa não tem moeda a informar */
    record LaborRateView(BigDecimal costPerHour, String currency) {}
}
