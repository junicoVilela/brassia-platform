package br.com.brew.brassia.recipe.adapter.inbound.web.dto;

import br.com.brew.brassia.recipe.application.port.inbound.CreateRecipeVersionUseCase;
import br.com.brew.brassia.recipe.application.port.inbound.PublishRecipeUseCase;
import java.util.UUID;

public record RecipeStatusResponse(UUID id, String name, String status, long version, UUID previousRecipeId) {

    public static RecipeStatusResponse from(PublishRecipeUseCase.Result r) {
        return new RecipeStatusResponse(r.id(), r.name(), r.status(), r.version(), null);
    }

    public static RecipeStatusResponse from(CreateRecipeVersionUseCase.Result r) {
        return new RecipeStatusResponse(r.id(), r.name(), r.status(), r.version(), r.previousRecipeId());
    }
}
