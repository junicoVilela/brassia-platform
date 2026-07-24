package br.com.brew.brassia.catalog.adapter.inbound.web;

import br.com.brew.brassia.catalog.adapter.inbound.web.dto.SubstitutionsResponse;
import br.com.brew.brassia.catalog.application.port.inbound.RankSubstitutionsUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalog/ingredients/{ingredientId}/substitutions")
final class SubstitutionController {

    private static final int DEFAULT_LIMIT = 10;

    private final RankSubstitutionsUseCase rank;

    SubstitutionController(RankSubstitutionsUseCase rank) {
        this.rank = rank;
    }

    @GetMapping
    ResponseEntity<SubstitutionsResponse> list(
            @PathVariable UUID ingredientId,
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("catalog.ingredient.read");
        return rank.handle(new RankSubstitutionsUseCase.Query(principal.requireBrewery(), ingredientId, limit))
                .map(SubstitutionsResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
