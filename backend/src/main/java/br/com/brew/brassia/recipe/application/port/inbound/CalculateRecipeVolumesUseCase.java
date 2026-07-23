package br.com.brew.brassia.recipe.application.port.inbound;

import java.math.BigDecimal;
import java.util.UUID;

@FunctionalInterface
public interface CalculateRecipeVolumesUseCase {
    Result handle(Query query);

    record Query(UUID breweryId, UUID recipeId) {}

    record Result(UUID recipeId, BigDecimal grainMassKg, BigDecimal finalVolumeLiters,
            BigDecimal grainAbsorptionLiters, BigDecimal evaporationLiters, BigDecimal lossesLiters,
            BigDecimal preBoilVolumeLiters, BigDecimal totalWaterLiters, String method) {}
}
