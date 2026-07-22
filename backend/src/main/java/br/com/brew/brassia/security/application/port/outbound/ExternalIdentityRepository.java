package br.com.brew.brassia.security.application.port.outbound;

import java.util.Optional;
import java.util.UUID;

public interface ExternalIdentityRepository {
    void link(UUID providerId, UUID userId, String externalSubject, String normalizedEmail);
    Optional<UUID> resolveUserId(UUID providerId, String externalSubject);
}
