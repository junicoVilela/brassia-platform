package br.com.brew.brassia.recipe.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateRecipeRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull UUID equipmentId,
        @NotNull BigDecimal batchVolumeLiters,
        BigDecimal targetOgPoints,
        BigDecimal targetIbu,
        BigDecimal targetColorEbc,
        BigDecimal targetAbv,
        Integer boilTimeMinutes,
        @NotEmpty List<Item> items) {

    public record Item(
            @NotNull UUID ingredientId,
            @NotBlank String stage,
            @NotNull BigDecimal quantity,
            @NotBlank String unit,
            Integer timingMinutes,
            BigDecimal percentage) {}
}
