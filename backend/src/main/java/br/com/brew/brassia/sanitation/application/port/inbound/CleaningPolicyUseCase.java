package br.com.brew.brassia.sanitation.application.port.inbound;

import br.com.brew.brassia.sanitation.domain.CleaningPolicy;
import java.util.UUID;

/** Leitura e ajuste da política de limpeza (PRM-001). */
public interface CleaningPolicyUseCase {

    CleaningPolicy get(UUID breweryId);

    /** @param validityHours {@code null} remove o prazo e volta a não expirar por tempo */
    CleaningPolicy update(UUID actorId, UUID breweryId, Integer validityHours);
}
