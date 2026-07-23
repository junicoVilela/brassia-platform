package br.com.brew.brassia.recipe.application.port.inbound;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface CreateRecipeUseCase {
    Result handle(Command command);

    /** Item da composição (referência ao ingrediente do catálogo). */
    record ItemInput(UUID ingredientId, String stage, BigDecimal quantity, String unit, Integer timingMinutes,
            BigDecimal percentage) {}

    record Command(UUID actorId, UUID breweryId, String name, UUID equipmentId, BigDecimal batchVolumeLiters,
            BigDecimal targetOgPoints, BigDecimal targetIbu, BigDecimal targetColorEbc, BigDecimal targetAbv,
            Integer boilTimeMinutes, List<ItemInput> items) {}

    record Result(UUID id, String name, String status) {}
}
