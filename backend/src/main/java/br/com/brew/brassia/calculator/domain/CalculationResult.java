package br.com.brew.brassia.calculator.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Resultado de uma calculadora determinística: valor, entradas, método, versão,
 * hipóteses e alertas. Determinístico — a mesma entrada produz a mesma saída.
 */
public record CalculationResult(String calculator, Map<String, BigDecimal> inputs, BigDecimal value, String unit,
        String method, String version, List<String> assumptions, List<String> alerts) {}
