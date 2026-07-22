package br.com.brew.brassia.shared.security;

import java.io.Serializable;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Principal de conta de serviço autenticada por API key (SEC-011). */
public record ServicePrincipal(
        UUID serviceAccountId,
        UUID breweryId,
        Set<String> scopes) implements Serializable {

    public ServicePrincipal {
        Objects.requireNonNull(serviceAccountId);
        Objects.requireNonNull(breweryId);
        scopes = Set.copyOf(scopes);
    }

    public void requireScope(String scope) {
        if (!scopes.contains(scope)) {
            throw new ForbiddenException("escopo insuficiente");
        }
    }
}
