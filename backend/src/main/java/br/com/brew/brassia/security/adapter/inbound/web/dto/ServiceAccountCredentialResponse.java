package br.com.brew.brassia.security.adapter.inbound.web.dto;

import br.com.brew.brassia.security.application.port.inbound.ManageServiceAccountUseCase;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Metadados de uma credencial de conta de serviço (SEC-B04). Nunca inclui o segredo. */
public record ServiceAccountCredentialResponse(
        UUID id, String keyPrefix, List<String> scopes, Instant expiresAt, Instant revokedAt, boolean active) {

    public static ServiceAccountCredentialResponse from(ManageServiceAccountUseCase.CredentialView view) {
        return new ServiceAccountCredentialResponse(
                view.id(), view.keyPrefix(), view.scopes(), view.expiresAt(), view.revokedAt(),
                view.revokedAt() == null);
    }
}
