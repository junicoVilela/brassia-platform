package br.com.brew.brassia.security.application.port.outbound;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExternalIdentityRepository {
    void link(UUID providerId, UUID userId, String externalSubject, String normalizedEmail);
    Optional<UUID> resolveUserId(UUID providerId, String externalSubject);

    /** Identidades externas vinculadas a um provedor (mais recentes primeiro). */
    List<IdentityView> listByProvider(UUID providerId);

    record IdentityView(UUID userId, String externalSubject, String normalizedEmail, Instant linkedAt) {}
}
