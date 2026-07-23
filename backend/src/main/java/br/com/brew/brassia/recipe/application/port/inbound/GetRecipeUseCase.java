package br.com.brew.brassia.recipe.application.port.inbound;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@FunctionalInterface
public interface GetRecipeUseCase {
    Result handle(Query query);

    record Query(UUID breweryId, UUID recipeId) {}

    record Item(UUID ingredientId, String stage, BigDecimal quantity, String unit, Integer timingMinutes,
            BigDecimal percentage) {}

    record Result(UUID id, String name, String status, UUID equipmentId, BigDecimal batchVolumeLiters,
            BigDecimal targetOgPoints, BigDecimal targetIbu, BigDecimal targetColorEbc, BigDecimal targetAbv,
            Integer boilTimeMinutes, List<Item> items, long version) {}
}
