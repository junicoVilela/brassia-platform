package br.com.brew.brassia.sanitation.application.port.outbound;

import br.com.brew.brassia.sanitation.domain.CleaningPolicy;
import java.util.UUID;

public interface CleaningPolicyRepository {

    /** Nunca vazio: cervejaria sem linha configurada devolve política sem prazo. */
    CleaningPolicy find(UUID breweryId);

    void save(CleaningPolicy policy);
}
