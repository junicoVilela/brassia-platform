package br.com.brew.brassia.catalog.adapter.inbound.web;

import br.com.brew.brassia.catalog.adapter.inbound.web.dto.IngredientResponse;
import br.com.brew.brassia.catalog.adapter.inbound.web.dto.RegisterIngredientRequest;
import br.com.brew.brassia.catalog.adapter.inbound.web.dto.UpdateIngredientRequest;
import br.com.brew.brassia.catalog.application.port.inbound.ListIngredientsUseCase;
import br.com.brew.brassia.catalog.application.port.inbound.RegisterIngredientUseCase;
import br.com.brew.brassia.catalog.application.port.inbound.UpdateIngredientUseCase;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalog/ingredients")
final class IngredientController {
    private final RegisterIngredientUseCase register;
    private final UpdateIngredientUseCase update;
    private final ListIngredientsUseCase list;

    IngredientController(RegisterIngredientUseCase register, UpdateIngredientUseCase update,
            ListIngredientsUseCase list) {
        this.register = register;
        this.update = update;
        this.list = list;
    }

    @GetMapping
    PageResponse<IngredientResponse> list(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("catalog.ingredient.read");
        var result = list.handle(new ListIngredientsUseCase.Query(principal.requireBrewery(), type, page, size));
        var content = result.content().stream().map(IngredientResponse::from).toList();
        return new PageResponse<>(content, result.page(), result.size(), result.totalElements(), result.totalPages());
    }

    @PostMapping
    ResponseEntity<IngredientResponse> create(
            @Valid @RequestBody RegisterIngredientRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("catalog.ingredient.manage");
        var result = register.handle(new RegisterIngredientUseCase.Command(
                principal.userId(), principal.requireBrewery(), request.type(), request.code(), request.name(),
                request.useUnit(), request.purchaseUnit(), request.attributes()));
        return ResponseEntity.created(URI.create("/api/v1/catalog/ingredients/" + result.id()))
                .body(IngredientResponse.from(result));
    }

    @PutMapping("/{id}")
    IngredientResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateIngredientRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("catalog.ingredient.manage");
        var result = update.handle(new UpdateIngredientUseCase.Command(
                principal.userId(), principal.requireBrewery(), id, request.name(), request.useUnit(),
                request.purchaseUnit(), request.attributes(), request.version()));
        return IngredientResponse.from(result);
    }
}
