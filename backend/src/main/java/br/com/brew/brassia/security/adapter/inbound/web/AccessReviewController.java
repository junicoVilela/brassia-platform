package br.com.brew.brassia.security.adapter.inbound.web;

import br.com.brew.brassia.security.adapter.inbound.web.dto.CreateAccessReviewRequest;
import br.com.brew.brassia.security.adapter.inbound.web.dto.DecideAccessReviewItemRequest;
import br.com.brew.brassia.security.adapter.inbound.web.dto.CreateSegregationRuleRequest;
import br.com.brew.brassia.security.application.port.inbound.ManageAccessReviewUseCase;
import br.com.brew.brassia.security.application.port.inbound.ManageSegregationUseCase;
import br.com.brew.brassia.security.application.port.outbound.AccessReviewRepository;
import br.com.brew.brassia.security.application.port.outbound.SegregationRuleRepository;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
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
@RequestMapping("/api/v1/security")
final class AccessReviewController {
    private final ManageAccessReviewUseCase accessReview;
    private final ManageSegregationUseCase segregation;

    AccessReviewController(ManageAccessReviewUseCase accessReview, ManageSegregationUseCase segregation) {
        this.accessReview = accessReview;
        this.segregation = segregation;
    }

    @GetMapping("/access-reviews")
    List<AccessReviewRepository.ReviewView> listReviews(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("security.access-review.read");
        return accessReview.listReviews(principal.requireBrewery());
    }

    @PostMapping("/access-reviews")
    ResponseEntity<Map<String, UUID>> createReview(
            @Valid @RequestBody CreateAccessReviewRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("security.access-review.manage");
        var id = accessReview.createReview(new ManageAccessReviewUseCase.CreateReviewCommand(
                principal.requireBrewery(), request.name(), principal.userId(), request.dueAt()));
        return ResponseEntity.created(URI.create("/api/v1/security/access-reviews/" + id)).body(Map.of("id", id));
    }

    @GetMapping("/access-reviews/{id}/items")
    List<AccessReviewRepository.ItemView> listItems(
            @PathVariable UUID id, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("security.access-review.read");
        return accessReview.listItems(id);
    }

    @PostMapping("/access-reviews/items/{itemId}/decide")
    ResponseEntity<Void> decide(
            @PathVariable UUID itemId,
            @Valid @RequestBody DecideAccessReviewItemRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("security.access-review.decide");
        accessReview.decideItem(new ManageAccessReviewUseCase.DecideItemCommand(
                principal.requireBrewery(), principal.userId(), itemId, request.decision(), request.justification()));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/segregation-rules")
    List<SegregationRuleRepository.RuleView> listRules(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("security.segregation.manage");
        return segregation.listRules(principal.requireBrewery());
    }

    @PostMapping("/segregation-rules")
    ResponseEntity<Map<String, UUID>> createRule(
            @Valid @RequestBody CreateSegregationRuleRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("security.segregation.manage");
        var id = segregation.createRule(new ManageSegregationUseCase.CreateRuleCommand(
                principal.requireBrewery(), principal.userId(), request.leftPermissionCode(),
                request.rightPermissionCode(), request.reason()));
        return ResponseEntity.created(URI.create("/api/v1/security/segregation-rules/" + id)).body(Map.of("id", id));
    }
}
