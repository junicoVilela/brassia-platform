package br.com.brew.brassia.referencedata.domain;

import java.math.BigDecimal;

/**
 * Resultado da comparação de um parâmetro da receita com a faixa do estilo.
 * {@code withinRange == false} é um aviso explicável — nunca bloqueia a receita.
 */
public record RangeCheck(String metric, BigDecimal value, BigDecimal min, BigDecimal max, String unit,
        boolean withinRange) {}
