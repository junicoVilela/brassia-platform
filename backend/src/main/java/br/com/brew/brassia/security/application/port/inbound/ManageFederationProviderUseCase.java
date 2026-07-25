package br.com.brew.brassia.security.application.port.inbound;

import br.com.brew.brassia.security.application.port.outbound.ExternalIdentityRepository;
import br.com.brew.brassia.security.application.port.outbound.FederationProviderRepository;
import br.com.brew.brassia.security.application.port.outbound.ScimGroupMappingRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ManageFederationProviderUseCase {
    UUID create(CreateCommand command);
    List<FederationProviderRepository.ProviderView> list(UUID breweryId);
    void validate(ValidateCommand command);
    void linkIdentity(LinkCommand command);
    UUID resolveUserId(UUID providerId, String externalSubject);

    /** Identidades externas vinculadas a um provedor da cervejaria (SEC-B06). */
    List<ExternalIdentityRepository.IdentityView> listIdentities(UUID breweryId, UUID providerId);

    /** Mapeamentos de grupo SCIM de um provedor (SEC-B05). */
    List<ScimGroupMappingRepository.MappingView> listScimMappings(UUID breweryId, UUID providerId);

    void upsertScimMapping(ScimMappingCommand command);

    void deactivateScimMapping(UUID breweryId, UUID actorId, UUID providerId, String externalGroupId);

    record ScimMappingCommand(UUID breweryId, UUID actorId, UUID providerId,
            String externalGroupId, UUID securityGroupId) {}

    record CreateCommand(UUID breweryId, UUID actorId, String code, String displayName,
            String protocol, String issuerOrEntityId, Map<String, Object> configuration) {}
    record ValidateCommand(UUID breweryId, UUID actorId, UUID providerId) {}
    record LinkCommand(UUID breweryId, UUID actorId, UUID providerId, UUID userId,
            String externalSubject, String normalizedEmail) {}
}
