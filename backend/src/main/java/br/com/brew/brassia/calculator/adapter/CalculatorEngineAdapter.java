package br.com.brew.brassia.calculator.adapter;

import br.com.brew.brassia.calculator.CalculatorEngine;
import br.com.brew.brassia.calculator.domain.Calculators;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Expõe o motor de cálculos (domínio) como API publicada para outros módulos (PRD-004). */
@Component
class CalculatorEngineAdapter implements CalculatorEngine {

    private final Calculators calculators;

    CalculatorEngineAdapter(Calculators calculators) {
        this.calculators = calculators;
    }

    @Override
    public List<CalculatorInfo> catalog() {
        return calculators.catalog().stream()
                .map(s -> new CalculatorInfo(s.id(), s.name(), s.inputs(), s.unit(), s.description()))
                .toList();
    }

    @Override
    public Computation compute(String id, Map<String, BigDecimal> inputs) {
        var r = calculators.compute(id, inputs == null ? Map.of() : inputs);
        return new Computation(r.calculator(), r.value(), r.unit(), r.method(), r.version(),
                r.assumptions(), r.alerts());
    }
}
