package br.com.brew.brassia.security.application.port.outbound;

import br.com.brew.brassia.security.domain.UserId;
import java.util.List;
import java.util.UUID;

/** Acesso do usuário a cervejarias, derivado das associações a grupos. */
public interface BreweryAccessRepository {
    /** true se o usuário tem alguma associação global (sem cervejaria) → acessa todas. */
    boolean hasGlobalMembership(UserId userId);

    /** Cervejarias das associações escopadas (ativas) do usuário. */
    List<UUID> scopedBreweryIds(UserId userId);
}
