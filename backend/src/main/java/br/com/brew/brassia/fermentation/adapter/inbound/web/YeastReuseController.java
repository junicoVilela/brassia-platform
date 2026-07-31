package br.com.brew.brassia.fermentation.adapter.inbound.web;

import br.com.brew.brassia.fermentation.adapter.inbound.web.dto.YeastPolicyDto;
import br.com.brew.brassia.fermentation.adapter.inbound.web.dto.YeastReuseView;
import br.com.brew.brassia.fermentation.application.port.inbound.RecommendYeastReuseUseCase;
import br.com.brew.brassia.fermentation.application.port.inbound.YeastPolicyUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recomendação de reutilização de levedura e a política que a rege (YST-002). Recomendar é
 * consulta: o uso continua exigindo confirmação explícita no endpoint da coleta.
 */
@RestController
@RequestMapping("/api/v1/fermentation/yeast")
final class YeastReuseController {

    private final RecommendYeastReuseUseCase recommend;
    private final YeastPolicyUseCase policy;

    YeastReuseController(RecommendYeastReuseUseCase recommend, YeastPolicyUseCase policy) {
        this.recommend = recommend;
        this.policy = policy;
    }

    @GetMapping("/reuse")
    YeastReuseView recommend(@RequestParam(required = false) UUID strainId,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("fermentation.yeast.read");
        return YeastReuseView.from(recommend.handle(principal.requireBrewery(), strainId));
    }

    @GetMapping("/policy")
    YeastPolicyDto policy(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("fermentation.yeast.read");
        return YeastPolicyDto.from(policy.get(principal.requireBrewery()));
    }

    @PutMapping("/policy")
    void savePolicy(@Valid @RequestBody YeastPolicyDto request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("fermentation.yeast.policy.manage");
        policy.save(principal.userId(), principal.requireBrewery(), request.maxGeneration(),
                request.maxAgeDays(), request.minViabilityPercent());
    }
}
