package br.com.brew.brassia.recipe.adapter.inbound.web;

import br.com.brew.brassia.recipe.adapter.inbound.web.dto.CloneRecipeRequest;
import br.com.brew.brassia.recipe.adapter.inbound.web.dto.RecipeComparisonResponse;
import br.com.brew.brassia.recipe.adapter.inbound.web.dto.RecipeStatusResponse;
import br.com.brew.brassia.recipe.adapter.inbound.web.dto.ScaleRecipeRequest;
import br.com.brew.brassia.recipe.application.port.inbound.CloneRecipeUseCase;
import br.com.brew.brassia.recipe.application.port.inbound.CompareRecipesUseCase;
import br.com.brew.brassia.recipe.application.port.inbound.ScaleRecipeUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Derivações de receita (REC-005): clonar, escalar e comparar. */
@RestController
@RequestMapping("/api/v1/recipes/{id}")
final class RecipeDerivationController {
    private final CloneRecipeUseCase cloneRecipe;
    private final ScaleRecipeUseCase scaleRecipe;
    private final CompareRecipesUseCase compareRecipes;

    RecipeDerivationController(CloneRecipeUseCase cloneRecipe, ScaleRecipeUseCase scaleRecipe,
            CompareRecipesUseCase compareRecipes) {
        this.cloneRecipe = cloneRecipe;
        this.scaleRecipe = scaleRecipe;
        this.compareRecipes = compareRecipes;
    }

    @PostMapping("/clone")
    RecipeStatusResponse clone(
            @PathVariable UUID id,
            @Valid @RequestBody CloneRecipeRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("recipe.create");
        return RecipeStatusResponse.from(cloneRecipe.handle(new CloneRecipeUseCase.Command(
                principal.userId(), principal.requireBrewery(), id, request.name())));
    }

    @PostMapping("/scale")
    RecipeStatusResponse scale(
            @PathVariable UUID id,
            @Valid @RequestBody ScaleRecipeRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("recipe.create");
        return RecipeStatusResponse.from(scaleRecipe.handle(new ScaleRecipeUseCase.Command(
                principal.userId(), principal.requireBrewery(), id, request.name(), request.batchVolumeLiters())));
    }

    @GetMapping("/compare")
    RecipeComparisonResponse compare(
            @PathVariable UUID id,
            @RequestParam("other") UUID other,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("recipe.read");
        return RecipeComparisonResponse.from(compareRecipes.handle(
                new CompareRecipesUseCase.Query(principal.requireBrewery(), id, other)));
    }
}
