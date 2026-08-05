package br.com.brew.brassia.foodsafety.adapter.outbound.gateway;

import br.com.brew.brassia.foodsafety.ChangeoverCheck;
import br.com.brew.brassia.foodsafety.application.port.inbound.AllergenQueries;
import br.com.brew.brassia.foodsafety.domain.AllergenCode;
import br.com.brew.brassia.foodsafety.domain.AllergenProfile;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Publica o veredito de troca para quem agenda o uso do equipamento (FDS-001). */
@Component
class ChangeoverCheckAdapter implements ChangeoverCheck {

    private final AllergenQueries queries;

    ChangeoverCheckAdapter(AllergenQueries queries) {
        this.queries = Objects.requireNonNull(queries);
    }

    @Override
    public Verdict check(UUID breweryId, UUID equipmentId, UUID incomingBatchId, UUID previousBatchId,
            Instant previousUseAt, Instant at) {
        var verdict = queries.changeover(breweryId, equipmentId, incomingBatchId, previousBatchId,
                previousUseAt, at);
        return new Verdict(verdict.allowed(), verdict.code(), verdict.detail(),
                verdict.allergens().stream().map(AllergenCode::value).toList(),
                verdict.gaps().stream().map(AllergenProfile.Gap::label).toList());
    }
}
