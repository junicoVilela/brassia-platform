package br.com.brew.brassia.security.application.port.outbound;

import br.com.brew.brassia.security.domain.UserId;
import java.util.Set;
import java.util.UUID;

/** Resolve as permissões efetivas de um usuário a partir dos grupos ativos. */
public interface EffectivePermissionsRepository {
    /**
     * Permissões efetivas na cervejaria ativa: associações globais (sem
     * cervejaria) valem sempre; associações escopadas valem só na sua cervejaria.
     *
     * @param activeBreweryId cervejaria ativa (pode ser nula → só as globais)
     */
    Set<String> findByUserId(UserId userId, UUID activeBreweryId);
}
