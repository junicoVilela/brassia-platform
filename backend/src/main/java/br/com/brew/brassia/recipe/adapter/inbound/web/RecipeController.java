package br.com.brew.brassia.recipe.adapter.inbound.web;

import br.com.brew.brassia.recipe.adapter.inbound.web.dto.CreateRecipeRequest;
import br.com.brew.brassia.recipe.adapter.inbound.web.dto.RecipeDetailResponse;
import br.com.brew.brassia.recipe.adapter.inbound.web.dto.RecipeResponse;
import br.com.brew.brassia.recipe.adapter.inbound.web.dto.RecipeSummaryResponse;
import br.com.brew.brassia.recipe.adapter.inbound.web.dto.CalculatedMetricsResponse;
import br.com.brew.brassia.recipe.adapter.inbound.web.dto.MetricsResponse;
import br.com.brew.brassia.recipe.adapter.inbound.web.dto.VolumeBalanceResponse;
import br.com.brew.brassia.recipe.application.port.inbound.CalculateRecipeMetricsUseCase;
import br.com.brew.brassia.recipe.application.port.inbound.CalculateRecipeVolumesUseCase;
import br.com.brew.brassia.recipe.application.port.inbound.CreateRecipeUseCase;
import br.com.brew.brassia.recipe.application.port.inbound.GetRecipeMetricsUseCase;
import br.com.brew.brassia.recipe.application.port.inbound.GetRecipeUseCase;
import br.com.brew.brassia.recipe.application.port.inbound.ListRecipesUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import br.com.brew.brassia.shared.web.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recipes")
final class RecipeController {
    private final CreateRecipeUseCase createRecipe;
    private final ListRecipesUseCase listRecipes;
    private final GetRecipeUseCase getRecipe;
    private final CalculateRecipeVolumesUseCase calculateVolumes;
    private final CalculateRecipeMetricsUseCase calculateMetrics;
    private final GetRecipeMetricsUseCase getMetrics;

    RecipeController(CreateRecipeUseCase createRecipe, ListRecipesUseCase listRecipes, GetRecipeUseCase getRecipe,
            CalculateRecipeVolumesUseCase calculateVolumes, CalculateRecipeMetricsUseCase calculateMetrics,
            GetRecipeMetricsUseCase getMetrics) {
        this.createRecipe = createRecipe;
        this.listRecipes = listRecipes;
        this.getRecipe = getRecipe;
        this.calculateVolumes = calculateVolumes;
        this.calculateMetrics = calculateMetrics;
        this.getMetrics = getMetrics;
    }

    @GetMapping
    PageResponse<RecipeSummaryResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("recipe.read");
        var result = listRecipes.handle(new ListRecipesUseCase.Query(principal.requireBrewery(), page, size));
        var content = result.content().stream().map(RecipeSummaryResponse::from).toList();
        return new PageResponse<>(content, result.page(), result.size(), result.totalElements(), result.totalPages());
    }

    @GetMapping("/{id}")
    RecipeDetailResponse detail(@PathVariable UUID id, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("recipe.read");
        return RecipeDetailResponse.from(getRecipe.handle(
                new GetRecipeUseCase.Query(principal.requireBrewery(), id)));
    }

    @GetMapping("/{id}/volumes")
    VolumeBalanceResponse volumes(@PathVariable UUID id, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("recipe.read");
        return VolumeBalanceResponse.from(calculateVolumes.handle(
                new CalculateRecipeVolumesUseCase.Query(principal.requireBrewery(), id)));
    }

    @GetMapping("/{id}/metrics")
    MetricsResponse metrics(@PathVariable UUID id, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("recipe.read");
        return MetricsResponse.from(getMetrics.handle(new GetRecipeMetricsUseCase.Query(principal.requireBrewery(), id)));
    }

    @PostMapping("/{id}/metrics")
    CalculatedMetricsResponse calculateMetrics(
            @PathVariable UUID id, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("recipe.create");
        return CalculatedMetricsResponse.from(calculateMetrics.handle(
                new CalculateRecipeMetricsUseCase.Command(principal.userId(), principal.requireBrewery(), id)));
    }

    @PostMapping
    ResponseEntity<RecipeResponse> create(
            @Valid @RequestBody CreateRecipeRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("recipe.create");
        // brewery_id é autoridade do principal (cervejaria ativa), nunca do corpo.
        var items = request.items().stream()
                .map(i -> new CreateRecipeUseCase.ItemInput(i.ingredientId(), i.stage(), i.quantity(), i.unit(),
                        i.timingMinutes(), i.percentage()))
                .toList();
        var result = createRecipe.handle(new CreateRecipeUseCase.Command(
                principal.userId(), principal.requireBrewery(), request.name(), request.equipmentId(),
                request.batchVolumeLiters(), request.targetOgPoints(), request.targetIbu(),
                request.targetColorEbc(), request.targetAbv(), request.boilTimeMinutes(), items));
        return ResponseEntity.created(URI.create("/api/v1/recipes/" + result.id()))
                .body(new RecipeResponse(result.id(), result.name(), result.status()));
    }
}
