package br.com.brew.brassia.fermentation.application.service;

import br.com.brew.brassia.fermentation.application.port.inbound.RecommendYeastReuseUseCase;
import br.com.brew.brassia.fermentation.application.port.outbound.YeastHarvestRepository;
import br.com.brew.brassia.fermentation.application.port.outbound.YeastPolicyRepository;
import br.com.brew.brassia.fermentation.domain.YeastPolicy;
import br.com.brew.brassia.fermentation.domain.YeastRecommendation;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Recomenda coletas para repitch (YST-002). Só coletas disponíveis entram: em quarentena,
 * reprovada ou já usada sequer é candidata. O resultado devolve a política aplicada junto
 * dos fatores, para a recomendação ser conferível — e não usa nada por conta própria.
 */
public final class RecommendYeastReuseHandler implements RecommendYeastReuseUseCase {

    private final YeastHarvestRepository harvests;
    private final YeastPolicyRepository policies;

    public RecommendYeastReuseHandler(YeastHarvestRepository harvests, YeastPolicyRepository policies) {
        this.harvests = Objects.requireNonNull(harvests);
        this.policies = Objects.requireNonNull(policies);
    }

    @Override
    public Result handle(UUID breweryId, UUID strainId) {
        var policy = policies.find(breweryId).orElseGet(YeastPolicy::defaults);
        var now = Instant.now();

        var candidates = harvests.findAll(breweryId, true).stream()
                .filter(h -> strainId == null || strainId.equals(h.strainId()))
                .map(h -> YeastRecommendation.evaluate(h, policy, now))
                .toList();

        return new Result(YeastRecommendation.rank(candidates), policy);
    }
}
