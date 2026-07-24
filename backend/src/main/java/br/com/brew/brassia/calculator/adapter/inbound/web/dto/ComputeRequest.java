package br.com.brew.brassia.calculator.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Map;

/** Entradas de uma calculadora (chave → valor). */
public record ComputeRequest(@NotNull Map<String, BigDecimal> inputs) {}
