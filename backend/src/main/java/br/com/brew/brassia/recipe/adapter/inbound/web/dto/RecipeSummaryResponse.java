package br.com.brew.brassia.recipe.adapter.inbound.web.dto;

import br.com.brew.brassia.recipe.application.port.inbound.ListRecipesUseCase;
import java.math.BigDecimal;
import java.util.UUID;

public record RecipeSummaryResponse(UUID id, String name, String status, BigDecimal batchVolumeLiters, long version) {

    public static RecipeSummaryResponse from(ListRecipesUseCase.Summary s) {
        return new RecipeSummaryResponse(s.id(), s.name(), s.status(), s.batchVolumeLiters(), s.version());
    }
}
