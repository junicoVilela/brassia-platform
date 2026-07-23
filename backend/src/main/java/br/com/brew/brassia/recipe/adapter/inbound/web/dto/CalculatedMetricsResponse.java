package br.com.brew.brassia.recipe.adapter.inbound.web.dto;

import br.com.brew.brassia.recipe.application.port.inbound.CalculateRecipeMetricsUseCase;
import java.math.BigDecimal;
import java.util.UUID;

public record CalculatedMetricsResponse(
        UUID recipeId,
        String method,
        int version,
        BigDecimal ogPoints,
        BigDecimal ogSg,
        BigDecimal fgPoints,
        BigDecimal fgSg,
        BigDecimal abv,
        BigDecimal ibu,
        BigDecimal colorEbc,
        BigDecimal attenuationPercent,
        Check ogCheck,
        Check ibuCheck,
        Check colorCheck,
        Check abvCheck) {

    public record Check(BigDecimal value, BigDecimal target, BigDecimal tolerance, BigDecimal deviation,
            Boolean withinTolerance) {}

    public static CalculatedMetricsResponse from(CalculateRecipeMetricsUseCase.Result r) {
        return new CalculatedMetricsResponse(r.recipeId(), r.method(), r.version(), r.ogPoints(), r.ogSg(),
                r.fgPoints(), r.fgSg(), r.abv(), r.ibu(), r.colorEbc(), r.attenuationPercent(),
                check(r.ogCheck()), check(r.ibuCheck()), check(r.colorCheck()), check(r.abvCheck()));
    }

    private static Check check(CalculateRecipeMetricsUseCase.Check c) {
        return new Check(c.value(), c.target(), c.tolerance(), c.deviation(), c.withinTolerance());
    }
}
