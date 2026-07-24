package br.com.brew.brassia.recipe.domain;

import java.math.BigDecimal;

/**
 * Orientação por atributo: valor alvo, faixa, situação e sugestão determinística
 * (explicando o impacto previsto). É apenas orientação — nada é aplicado.
 */
public record AttributeGuidance(String attribute, BigDecimal value, BigDecimal min, BigDecimal max, String unit,
        GuidanceStatus status, String suggestion) {}
