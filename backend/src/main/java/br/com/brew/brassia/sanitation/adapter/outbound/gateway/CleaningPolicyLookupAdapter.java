package br.com.brew.brassia.sanitation.adapter.outbound.gateway;

import br.com.brew.brassia.sanitation.CleaningPolicyLookup;
import br.com.brew.brassia.sanitation.application.port.outbound.CleaningPolicyRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Publica a política de limpeza para outros módulos (PRM-001). O envase pergunta se a liberação
 * ainda cobre o início planejado, sem saber quantas horas são nem conhecer a tabela.
 */
@Component
class CleaningPolicyLookupAdapter implements CleaningPolicyLookup {

    private final CleaningPolicyRepository policies;

    CleaningPolicyLookupAdapter(CleaningPolicyRepository policies) {
        this.policies = Objects.requireNonNull(policies);
    }

    @Override
    public Optional<Integer> validityHours(UUID breweryId) {
        return policies.find(breweryId).validityHours();
    }

    @Override
    public boolean covers(UUID breweryId, Instant releasedAt, Instant at) {
        return policies.find(breweryId).covers(releasedAt, at);
    }
}
