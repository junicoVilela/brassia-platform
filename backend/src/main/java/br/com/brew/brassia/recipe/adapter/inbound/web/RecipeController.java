package br.com.brew.brassia.recipe.adapter.inbound.web;

import br.com.brew.brassia.recipe.adapter.inbound.web.dto.CreateRecipeRequest;
import br.com.brew.brassia.recipe.adapter.inbound.web.dto.RecipeResponse;
import br.com.brew.brassia.recipe.application.port.inbound.CreateRecipeUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recipes")
final class RecipeController {
    private final CreateRecipeUseCase createRecipe;

    RecipeController(CreateRecipeUseCase createRecipe) {
        this.createRecipe = createRecipe;
    }

    @PostMapping
    ResponseEntity<RecipeResponse> create(
            @Valid @RequestBody CreateRecipeRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("recipe.create");
        // brewery_id é autoridade do principal (cervejaria ativa), nunca do corpo.
        var result = createRecipe.handle(new CreateRecipeUseCase.Command(principal.requireBrewery(), request.name()));
        return ResponseEntity.created(URI.create("/api/v1/recipes/" + result.id()))
                .body(new RecipeResponse(result.id(), result.name(), result.status()));
    }
}
