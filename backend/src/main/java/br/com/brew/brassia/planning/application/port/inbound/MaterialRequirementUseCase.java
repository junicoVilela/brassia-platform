package br.com.brew.brassia.planning.application.port.inbound;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Necessidade de materiais ad-hoc: receita publicada + volume alvo + perda opcional. */
public interface MaterialRequirementUseCase {
    List<Line> handle(Query query);

    record Query(UUID breweryId, UUID recipeId, BigDecimal targetVolumeLiters, BigDecimal lossPercent) {}

    record Line(UUID ingredientId, BigDecimal requiredQuantity, String unit) {}
}
