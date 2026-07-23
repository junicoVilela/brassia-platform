package br.com.brew.brassia.recipe.adapter.inbound.web.dto;

import br.com.brew.brassia.recipe.application.port.inbound.GetRecipeUseCase;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record RecipeDetailResponse(
        UUID id,
        String name,
        String status,
        UUID equipmentId,
        BigDecimal batchVolumeLiters,
        BigDecimal targetOgPoints,
        BigDecimal targetIbu,
        BigDecimal targetColorEbc,
        BigDecimal targetAbv,
        Integer boilTimeMinutes,
        List<Item> items,
        long version) {

    public record Item(UUID ingredientId, String stage, BigDecimal quantity, String unit, Integer timingMinutes,
            BigDecimal percentage) {}

    public static RecipeDetailResponse from(GetRecipeUseCase.Result r) {
        var items = r.items().stream()
                .map(i -> new Item(i.ingredientId(), i.stage(), i.quantity(), i.unit(), i.timingMinutes(),
                        i.percentage()))
                .toList();
        return new RecipeDetailResponse(r.id(), r.name(), r.status(), r.equipmentId(), r.batchVolumeLiters(),
                r.targetOgPoints(), r.targetIbu(), r.targetColorEbc(), r.targetAbv(), r.boilTimeMinutes(), items,
                r.version());
    }
}
