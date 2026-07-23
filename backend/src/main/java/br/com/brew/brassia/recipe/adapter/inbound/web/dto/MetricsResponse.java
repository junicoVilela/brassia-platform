package br.com.brew.brassia.recipe.adapter.inbound.web.dto;

import br.com.brew.brassia.recipe.domain.RecipeMetrics;
import java.math.BigDecimal;
import java.util.UUID;

public record MetricsResponse(
        UUID recipeId,
        BigDecimal ogPoints,
        BigDecimal ogSg,
        BigDecimal fgPoints,
        BigDecimal fgSg,
        BigDecimal abv,
        BigDecimal ibu,
        BigDecimal colorEbc,
        BigDecimal attenuationPercent,
        String method,
        int version) {

    public static MetricsResponse from(RecipeMetrics m) {
        return new MetricsResponse(m.recipeId(), m.ogPoints(), m.ogSg(), m.fgPoints(), m.fgSg(), m.abv(), m.ibu(),
                m.colorEbc(), m.attenuationPercent(), m.method(), m.version());
    }
}
