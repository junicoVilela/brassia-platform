package br.com.brew.brassia.planning.adapter.inbound.web.dto;

import br.com.brew.brassia.planning.domain.BrewOrder;
import java.math.BigDecimal;
import java.util.UUID;

/** Resumo de OP para listagem. */
public record BrewOrderSummaryView(UUID id, String code, UUID recipeId, int recipeVersion, String recipeName,
        BigDecimal volumeLiters, String status) {

    public static BrewOrderSummaryView from(BrewOrder o) {
        return new BrewOrderSummaryView(o.id().value(), o.code(), o.recipeId(), o.recipeVersion(),
                o.snapshot().recipe().name(), o.volumeLiters(), o.status().name());
    }
}
