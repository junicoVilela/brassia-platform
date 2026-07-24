package br.com.brew.brassia.calculator.domain;

import java.util.List;

/** Descrição de uma calculadora do hub: id, nome, entradas exigidas e unidade. */
public record CalculatorSpec(String id, String name, List<String> inputs, String unit, String description) {}
