package br.com.brew.brassia.calculator;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Motor de cálculos determinísticos publicado para outros módulos (PRD-004
 * reutiliza as correções aqui). Mantém a mesma fórmula/versão da calculadora —
 * nada é replicado.
 */
public interface CalculatorEngine {
    List<CalculatorInfo> catalog();

    Computation compute(String id, Map<String, BigDecimal> inputs);

    record CalculatorInfo(String id, String name, List<String> inputs, String unit, String description) {}

    record Computation(String calculator, BigDecimal value, String unit, String method, String version,
            List<String> assumptions, List<String> alerts) {}
}
