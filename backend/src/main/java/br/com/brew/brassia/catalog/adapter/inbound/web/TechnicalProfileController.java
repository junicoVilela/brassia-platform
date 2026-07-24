package br.com.brew.brassia.catalog.adapter.inbound.web;

import br.com.brew.brassia.catalog.adapter.inbound.web.dto.CreateTechnicalProfileRequest;
import br.com.brew.brassia.catalog.adapter.inbound.web.dto.TechnicalProfileActionResponse;
import br.com.brew.brassia.catalog.adapter.inbound.web.dto.TechnicalProfileResponse;
import br.com.brew.brassia.catalog.application.port.inbound.CreateTechnicalProfileUseCase;
import br.com.brew.brassia.catalog.application.port.inbound.PublishTechnicalProfileUseCase;
import br.com.brew.brassia.catalog.application.port.inbound.TechnicalProfileUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalog/ingredients/{ingredientId}/technical-profile")
final class TechnicalProfileController {

    private final CreateTechnicalProfileUseCase create;
    private final PublishTechnicalProfileUseCase publish;
    private final TechnicalProfileUseCase get;

    TechnicalProfileController(CreateTechnicalProfileUseCase create, PublishTechnicalProfileUseCase publish,
            TechnicalProfileUseCase get) {
        this.create = create;
        this.publish = publish;
        this.get = get;
    }

    @GetMapping
    ResponseEntity<TechnicalProfileResponse> get(
            @PathVariable UUID ingredientId,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("catalog.ingredient.read");
        return get.handle(new TechnicalProfileUseCase.Query(principal.requireBrewery(), ingredientId))
                .map(TechnicalProfileResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    ResponseEntity<TechnicalProfileActionResponse> create(
            @PathVariable UUID ingredientId,
            @Valid @RequestBody CreateTechnicalProfileRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("catalog.ingredient.manage");
        var result = create.handle(new CreateTechnicalProfileUseCase.Command(
                principal.userId(), principal.requireBrewery(), ingredientId, request.manufacturer(),
                request.origin(), request.form(), request.purpose(), request.laboratory(), request.labCode(),
                request.toRanges(), request.descriptors(), request.sourceId(), request.sourceName()));
        return ResponseEntity
                .created(URI.create("/api/v1/catalog/ingredients/" + ingredientId + "/technical-profile"))
                .body(new TechnicalProfileActionResponse(result.id(), result.status()));
    }

    @PostMapping("/publish")
    TechnicalProfileActionResponse publish(
            @PathVariable UUID ingredientId,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("catalog.ingredient.manage");
        var result = publish.handle(new PublishTechnicalProfileUseCase.Command(
                principal.userId(), principal.requireBrewery(), ingredientId));
        return new TechnicalProfileActionResponse(null, result.status());
    }
}
