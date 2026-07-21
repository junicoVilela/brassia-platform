package br.com.brew.brassia.security.application.service;

import br.com.brew.brassia.brewery.BreweryDirectory;
import br.com.brew.brassia.brewery.BreweryRef;
import br.com.brew.brassia.security.application.port.outbound.BreweryAccessRepository;
import br.com.brew.brassia.security.application.port.outbound.EffectivePermissionsRepository;
import br.com.brew.brassia.security.domain.UserId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;

/**
 * Monta o contexto de sessão de um usuário: cervejarias acessíveis, cervejaria
 * ativa e permissões escopadas à ativa. Associação global dá acesso a todas as
 * cervejarias; associação escopada, apenas à sua.
 */
public final class SessionContextResolver {
    private final BreweryAccessRepository breweryAccess;
    private final BreweryDirectory breweryDirectory;
    private final EffectivePermissionsRepository permissions;

    public SessionContextResolver(
            BreweryAccessRepository breweryAccess,
            BreweryDirectory breweryDirectory,
            EffectivePermissionsRepository permissions) {
        this.breweryAccess = Objects.requireNonNull(breweryAccess);
        this.breweryDirectory = Objects.requireNonNull(breweryDirectory);
        this.permissions = Objects.requireNonNull(permissions);
    }

    /**
     * @param requestedBreweryId cervejaria pedida (troca); nula usa a padrão (primeira por código)
     * @throws AccessDeniedException se a cervejaria pedida não é acessível
     */
    public SessionContext resolve(UserId userId, UUID requestedBreweryId) {
        var accessible = accessibleBreweries(userId);
        var active = chooseActive(accessible, requestedBreweryId);
        return new SessionContext(active, accessible, permissions.findByUserId(userId, active));
    }

    private List<BreweryRef> accessibleBreweries(UserId userId) {
        List<BreweryRef> breweries = breweryAccess.hasGlobalMembership(userId)
                ? breweryDirectory.findAll()
                : breweryAccess.scopedBreweryIds(userId).stream()
                        .map(breweryDirectory::findById)
                        .flatMap(Optional::stream)
                        .toList();
        return breweries.stream().sorted(Comparator.comparing(BreweryRef::code)).toList();
    }

    private static UUID chooseActive(List<BreweryRef> accessible, UUID requestedBreweryId) {
        if (requestedBreweryId != null) {
            var accessibleRequested = accessible.stream().anyMatch(b -> b.id().equals(requestedBreweryId));
            if (!accessibleRequested) {
                throw new AccessDeniedException("cervejaria não acessível");
            }
            return requestedBreweryId;
        }
        return accessible.isEmpty() ? null : accessible.get(0).id();
    }
}
