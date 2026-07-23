package br.com.brew.brassia.recipe.application.port.inbound;

import java.math.BigDecimal;
import java.util.UUID;

public interface CalculateRecipeMetricsUseCase {
    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID recipeId) {}

    /** Comparação de uma meta calculada com a informada, com tolerância explícita. */
    record Check(BigDecimal value, BigDecimal target, BigDecimal tolerance, BigDecimal deviation,
            Boolean withinTolerance) {}

    record Result(UUID recipeId, String method, int version, BigDecimal ogPoints, BigDecimal ogSg,
            BigDecimal fgPoints, BigDecimal fgSg, BigDecimal abv, BigDecimal ibu, BigDecimal colorEbc,
            BigDecimal attenuationPercent, Check ogCheck, Check ibuCheck, Check colorCheck, Check abvCheck) {}
}
