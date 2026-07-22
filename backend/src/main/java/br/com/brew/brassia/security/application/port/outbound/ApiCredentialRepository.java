package br.com.brew.brassia.security.application.port.outbound;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiCredentialRepository {
    record CredentialView(UUID id, String keyPrefix, List<String> scopes, Instant expiresAt, Instant revokedAt) {}
    record ActiveCredential(UUID id, UUID serviceAccountId, UUID breweryId, String keyHash, List<String> scopes) {}

    UUID issue(UUID serviceAccountId, String keyPrefix, String keyHash, List<String> scopes, Instant expiresAt);
    void revoke(UUID credentialId);
    List<CredentialView> listByServiceAccount(UUID serviceAccountId);
    Optional<ActiveCredential> findActiveByPrefix(String prefix);
    void touchLastUsed(UUID credentialId, Instant now);
}
