package br.com.brew.brassia.security.adapter.inbound.web.dto;

import br.com.brew.brassia.security.application.port.outbound.ExternalIdentityRepository;
import java.time.Instant;
import java.util.UUID;

/** Identidade externa vinculada a um provedor de federação (SEC-B06). */
public record ExternalIdentityResponse(UUID userId, String externalSubject, String normalizedEmail, Instant linkedAt) {

    public static ExternalIdentityResponse from(ExternalIdentityRepository.IdentityView view) {
        return new ExternalIdentityResponse(
                view.userId(), view.externalSubject(), view.normalizedEmail(), view.linkedAt());
    }
}
