package br.com.brew.brassia.recipe.adapter.inbound.web.exchange;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Modelo neutro de intercâmbio de receita (REC-006), independente do formato
 * (BeerJSON/BeerXML). Referencia equipamento e ingredientes por id interno.
 */
public record RecipeDocument(
        String name,
        UUID equipmentId,
        BigDecimal batchVolumeLiters,
        Integer boilTimeMinutes,
        BigDecimal targetOgPoints,
        BigDecimal targetIbu,
        BigDecimal targetColorEbc,
        BigDecimal targetAbv,
        List<Item> items) {

    public record Item(UUID ingredientId, String stage, BigDecimal quantity, String unit, Integer timingMinutes,
            BigDecimal percentage) {}
}
