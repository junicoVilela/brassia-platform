package br.com.brew.brassia.recipe.adapter.inbound.web.dto;

import br.com.brew.brassia.recipe.application.port.inbound.CompareRecipesUseCase;
import java.util.List;
import java.util.UUID;

public record RecipeComparisonResponse(UUID leftId, UUID rightId, List<Difference> differences) {

    public record Difference(String field, String left, String right) {}

    public static RecipeComparisonResponse from(CompareRecipesUseCase.Result r) {
        var differences = r.differences().stream()
                .map(d -> new Difference(d.field(), d.left(), d.right()))
                .toList();
        return new RecipeComparisonResponse(r.leftId(), r.rightId(), differences);
    }
}
