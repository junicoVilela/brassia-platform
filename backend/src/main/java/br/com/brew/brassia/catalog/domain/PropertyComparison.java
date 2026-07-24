package br.com.brew.brassia.catalog.domain;

import java.math.BigDecimal;

/** Comparação de uma propriedade entre alvo e candidato (ponto médio da faixa). */
public record PropertyComparison(String property, BigDecimal target, BigDecimal candidate, String unit,
        boolean similar) {}
