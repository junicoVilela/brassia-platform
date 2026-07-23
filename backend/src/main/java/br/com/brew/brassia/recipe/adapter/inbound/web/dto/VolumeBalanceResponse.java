package br.com.brew.brassia.recipe.adapter.inbound.web.dto;

import br.com.brew.brassia.recipe.application.port.inbound.CalculateRecipeVolumesUseCase;
import java.math.BigDecimal;
import java.util.UUID;

public record VolumeBalanceResponse(
        UUID recipeId,
        BigDecimal grainMassKg,
        BigDecimal finalVolumeLiters,
        BigDecimal grainAbsorptionLiters,
        BigDecimal evaporationLiters,
        BigDecimal lossesLiters,
        BigDecimal preBoilVolumeLiters,
        BigDecimal totalWaterLiters,
        String method) {

    public static VolumeBalanceResponse from(CalculateRecipeVolumesUseCase.Result r) {
        return new VolumeBalanceResponse(r.recipeId(), r.grainMassKg(), r.finalVolumeLiters(),
                r.grainAbsorptionLiters(), r.evaporationLiters(), r.lossesLiters(), r.preBoilVolumeLiters(),
                r.totalWaterLiters(), r.method());
    }
}
