package br.com.brew.brassia.sanitation.adapter.inbound.web;

import br.com.brew.brassia.sanitation.adapter.inbound.web.dto.CreateRuleRequest;
import br.com.brew.brassia.sanitation.adapter.inbound.web.dto.RecommendRequest;
import br.com.brew.brassia.sanitation.adapter.inbound.web.dto.RuleView;
import br.com.brew.brassia.sanitation.application.port.inbound.CreateRuleUseCase;
import br.com.brew.brassia.sanitation.application.port.inbound.ListRulesUseCase;
import br.com.brew.brassia.sanitation.application.port.inbound.RecommendUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Matriz de compatibilidade de limpeza/sanitização (CLN-002). */
@RestController
@RequestMapping("/api/v1/sanitation/matrix")
final class CompatibilityMatrixController {

    private final CreateRuleUseCase createRule;
    private final ListRulesUseCase listRules;
    private final RecommendUseCase recommend;

    CompatibilityMatrixController(CreateRuleUseCase createRule, ListRulesUseCase listRules,
            RecommendUseCase recommend) {
        this.createRule = createRule;
        this.listRules = listRules;
        this.recommend = recommend;
    }

    @GetMapping
    List<RuleView> list(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("sanitation.matrix.read");
        return listRules.handle(principal.requireBrewery()).stream().map(RuleView::from).toList();
    }

    @PostMapping
    ResponseEntity<Map<String, Object>> create(
            @Valid @RequestBody CreateRuleRequest request, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("sanitation.matrix.manage");
        var id = createRule.handle(new CreateRuleUseCase.Command(
                principal.userId(), principal.requireBrewery(), request.material(), request.soiling(),
                request.risk(), request.previousProduct(), request.procedureCode(), request.method(),
                request.alternative(), request.restriction()));
        return ResponseEntity.created(URI.create("/api/v1/sanitation/matrix/" + id)).body(Map.of("id", id));
    }

    @PostMapping("/recommend")
    RuleView recommend(@Valid @RequestBody RecommendRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("sanitation.matrix.read");
        return RuleView.from(recommend.handle(new RecommendUseCase.Command(
                principal.requireBrewery(), request.material(), request.soiling(), request.risk(),
                request.previousProduct())));
    }
}
