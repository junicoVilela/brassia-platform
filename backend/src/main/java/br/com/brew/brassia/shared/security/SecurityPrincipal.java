package br.com.brew.brassia.shared.security;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;

public record SecurityPrincipal(
        UUID userId,
        UUID breweryId,
        String displayName,
        Set<String> permissions) {

    public SecurityPrincipal {
        Objects.requireNonNull(userId);
        Objects.requireNonNull(breweryId);
        displayName = Objects.requireNonNull(displayName);
        permissions = Set.copyOf(permissions);
    }

    public void requirePermission(String permission) {
        if (!permissions.contains(permission)) {
            throw new AccessDeniedException("permission denied");
        }
    }
}
