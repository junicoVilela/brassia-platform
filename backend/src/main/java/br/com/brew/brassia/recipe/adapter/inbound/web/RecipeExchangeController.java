package br.com.brew.brassia.recipe.adapter.inbound.web;

import br.com.brew.brassia.recipe.adapter.inbound.web.dto.ImportReportResponse;
import br.com.brew.brassia.recipe.adapter.inbound.web.exchange.RecipeDocument;
import br.com.brew.brassia.recipe.adapter.inbound.web.exchange.RecipeExchangeCodec;
import br.com.brew.brassia.recipe.application.port.inbound.CreateRecipeUseCase;
import br.com.brew.brassia.recipe.application.port.inbound.RecipeUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Importação/exportação de receitas em BeerJSON/BeerXML (REC-006). */
@RestController
@RequestMapping("/api/v1/recipes")
final class RecipeExchangeController {
    private final RecipeUseCase getRecipe;
    private final CreateRecipeUseCase createRecipe;
    private final RecipeExchangeCodec codec;

    RecipeExchangeController(RecipeUseCase getRecipe, CreateRecipeUseCase createRecipe, RecipeExchangeCodec codec) {
        this.getRecipe = getRecipe;
        this.createRecipe = createRecipe;
        this.codec = codec;
    }

    @GetMapping("/{id}/export")
    ResponseEntity<String> export(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "beerjson") String format,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("recipe.read");
        var fmt = codec.format(format);
        var recipe = getRecipe.handle(new RecipeUseCase.Query(principal.requireBrewery(), id));
        var document = toDocument(recipe);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, codec.contentType(fmt))
                .body(codec.write(fmt, document));
    }

    @PostMapping("/import")
    ImportReportResponse importRecipe(
            @RequestParam(defaultValue = "beerjson") String format,
            @RequestBody byte[] body,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("recipe.create");
        var fmt = codec.format(format);
        var parsed = codec.parse(fmt, new String(body, StandardCharsets.UTF_8));
        var d = parsed.document();
        var items = d.items().stream()
                .map(i -> new CreateRecipeUseCase.ItemInput(i.ingredientId(), i.stage(), i.quantity(), i.unit(),
                        i.timingMinutes(), i.percentage()))
                .toList();
        // CreateRecipe valida e persiste atomicamente; importação inválida não persiste (400).
        var result = createRecipe.handle(new CreateRecipeUseCase.Command(
                principal.userId(), principal.requireBrewery(), d.name(), d.equipmentId(), d.batchVolumeLiters(),
                d.targetOgPoints(), d.targetIbu(), d.targetColorEbc(), d.targetAbv(), d.boilTimeMinutes(), items));
        return new ImportReportResponse(result.id(), result.name(), result.status(), parsed.unknownFields());
    }

    private static RecipeDocument toDocument(RecipeUseCase.Result r) {
        var items = r.items().stream()
                .map(i -> new RecipeDocument.Item(i.ingredientId(), i.stage(), i.quantity(), i.unit(),
                        i.timingMinutes(), i.percentage()))
                .toList();
        return new RecipeDocument(r.name(), r.equipmentId(), r.batchVolumeLiters(), r.boilTimeMinutes(),
                r.targetOgPoints(), r.targetIbu(), r.targetColorEbc(), r.targetAbv(), items);
    }
}
