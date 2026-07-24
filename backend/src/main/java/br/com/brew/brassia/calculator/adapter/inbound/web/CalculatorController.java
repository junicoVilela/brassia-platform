package br.com.brew.brassia.calculator.adapter.inbound.web;

import br.com.brew.brassia.calculator.adapter.inbound.web.dto.ComputeRequest;
import br.com.brew.brassia.calculator.domain.CalculationResult;
import br.com.brew.brassia.calculator.domain.CalculatorSpec;
import br.com.brew.brassia.calculator.domain.Calculators;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Hub de calculadoras cervejeiras determinísticas (CAL-001). */
@RestController
@RequestMapping("/api/v1/calculators")
final class CalculatorController {

    private final Calculators calculators;

    CalculatorController(Calculators calculators) {
        this.calculators = calculators;
    }

    @GetMapping
    List<CalculatorSpec> catalog(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("recipe.read");
        return calculators.catalog();
    }

    @PostMapping("/{id}")
    CalculationResult compute(
            @PathVariable String id,
            @Valid @RequestBody ComputeRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("recipe.read");
        Map<String, java.math.BigDecimal> inputs = request.inputs() == null ? Map.of() : request.inputs();
        // Entrada ausente/calculadora desconhecida → IllegalArgumentException (400).
        return calculators.compute(id, inputs);
    }
}
