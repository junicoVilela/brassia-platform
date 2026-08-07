package br.com.brew.brassia.security.application.service;

import br.com.brew.brassia.security.EffectivePermissionLookup;
import br.com.brew.brassia.security.application.port.outbound.EffectivePermissionsRepository;
import br.com.brew.brassia.security.domain.UserId;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Publica para fora as permissões efetivas que a sessão já usa por dentro (RPT-003).
 *
 * <p>Passa pelo mesmo repositório do login, e não por um SQL próprio: fosse por SQL, um relatório
 * programado poderia entregar dado que a tela recusa mostrar ao mesmo usuário, e a divergência só
 * apareceria quando alguém comparasse os dois.
 */
public final class EffectivePermissionService implements EffectivePermissionLookup {

    private final EffectivePermissionsRepository permissions;

    public EffectivePermissionService(EffectivePermissionsRepository permissions) {
        this.permissions = Objects.requireNonNull(permissions);
    }

    @Override
    public Set<String> permissionsOf(UUID userId, UUID breweryId) {
        if (userId == null) {
            return Set.of();
        }
        return permissions.findByUserId(new UserId(userId), breweryId);
    }
}
