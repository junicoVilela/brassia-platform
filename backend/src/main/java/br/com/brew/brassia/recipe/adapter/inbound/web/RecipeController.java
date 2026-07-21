package br.com.brew.brassia.recipe.adapter.inbound.web;

import br.com.brew.brassia.recipe.application.port.inbound.CreateRecipeUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
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
    ResponseEntity<Response> create(
            @Valid @RequestBody Request request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("recipe.create");
        var result = createRecipe.handle(new CreateRecipeUseCase.Command(principal.breweryId(), request.name()));
        return ResponseEntity.created(URI.create("/api/v1/recipes/" + result.id()))
                .body(new Response(result.id(), result.name(), result.status()));
    }

    record Request(@NotBlank @Size(max = 120) String name) {}
    record Response(UUID id, String name, String status) {}
}
