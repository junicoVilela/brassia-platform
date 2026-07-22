package br.com.brew.brassia.security.application.port.inbound;

import br.com.brew.brassia.security.application.port.outbound.FederationProviderRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ManageFederationProviderUseCase {
    UUID create(CreateCommand command);
    List<FederationProviderRepository.ProviderView> list(UUID breweryId);
    void validate(ValidateCommand command);
    void linkIdentity(LinkCommand command);
    UUID resolveUserId(UUID providerId, String externalSubject);

    record CreateCommand(UUID breweryId, UUID actorId, String code, String displayName,
            String protocol, String issuerOrEntityId, Map<String, Object> configuration) {}
    record ValidateCommand(UUID breweryId, UUID actorId, UUID providerId) {}
    record LinkCommand(UUID breweryId, UUID actorId, UUID providerId, UUID userId,
            String externalSubject, String normalizedEmail) {}
}
