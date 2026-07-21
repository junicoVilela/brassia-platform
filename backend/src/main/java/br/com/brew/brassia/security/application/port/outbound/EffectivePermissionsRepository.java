package br.com.brew.brassia.security.application.port.outbound;

import br.com.brew.brassia.security.domain.UserId;
import java.util.Set;

/** Resolve as permissões efetivas de um usuário a partir dos grupos ativos. */
public interface EffectivePermissionsRepository {
    Set<String> findByUserId(UserId userId);
}
