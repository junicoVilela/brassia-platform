package br.com.brew.brassia.security.application.port.inbound;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ManageServiceAccountUseCase {
    ServiceAccountView create(CreateCommand command);
    List<ServiceAccountView> list(ListCommand command);
    IssueCredentialResult issueCredential(IssueCommand command);
    void revokeCredential(RevokeCommand command);

    /** Credenciais (metadados) de uma conta de serviço; nunca inclui o segredo (SEC-B04). */
    List<CredentialView> listCredentials(ListCredentialsCommand command);

    record CreateCommand(UUID actorId, UUID breweryId, String code, String name) {}
    record ListCommand(UUID breweryId) {}
    record IssueCommand(UUID actorId, UUID breweryId, UUID serviceAccountId, List<String> scopes) {}
    record RevokeCommand(UUID actorId, UUID breweryId, UUID credentialId) {}
    record ListCredentialsCommand(UUID breweryId, UUID serviceAccountId) {}

    record ServiceAccountView(UUID id, String code, String name, boolean active) {}
    record IssueCredentialResult(UUID credentialId, String rawKey, String keyPrefix) {}
    record CredentialView(UUID id, String keyPrefix, List<String> scopes, Instant expiresAt, Instant revokedAt) {}
}
