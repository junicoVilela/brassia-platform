package br.com.brew.brassia.security.application.port.inbound;

import java.util.List;
import java.util.UUID;

public interface ManageServiceAccountUseCase {
    ServiceAccountView create(CreateCommand command);
    List<ServiceAccountView> list(ListCommand command);
    IssueCredentialResult issueCredential(IssueCommand command);
    void revokeCredential(RevokeCommand command);

    record CreateCommand(UUID actorId, UUID breweryId, String code, String name) {}
    record ListCommand(UUID breweryId) {}
    record IssueCommand(UUID actorId, UUID breweryId, UUID serviceAccountId, List<String> scopes) {}
    record RevokeCommand(UUID actorId, UUID breweryId, UUID credentialId) {}

    record ServiceAccountView(UUID id, String code, String name, boolean active) {}
    record IssueCredentialResult(UUID credentialId, String rawKey, String keyPrefix) {}
}
