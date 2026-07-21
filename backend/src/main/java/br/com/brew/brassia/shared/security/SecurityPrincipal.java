package br.com.brew.brassia.shared.security;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;

/**
 * Identidade autenticada usada pelos casos de uso. {@code breweryId} é opcional:
 * enquanto a cervejaria ativa e as permissões (SEC-004/005) não são resolvidas,
 * o principal carrega apenas a identidade, sem tenant e sem permissões.
 */
public record SecurityPrincipal(
        UUID userId,
        UUID breweryId,
        String displayName,
        Set<String> permissions) {

    public SecurityPrincipal {
        Objects.requireNonNull(userId);
        displayName = Objects.requireNonNull(displayName);
        permissions = Set.copyOf(permissions);
    }

    /** Principal recém-autenticado: identidade apenas, sem cervejaria nem permissões. */
    public static SecurityPrincipal identityOnly(UUID userId, String displayName) {
        return new SecurityPrincipal(userId, null, displayName, Set.of());
    }

    public void requirePermission(String permission) {
        if (!permissions.contains(permission)) {
            throw new AccessDeniedException("permission denied");
        }
    }
}
