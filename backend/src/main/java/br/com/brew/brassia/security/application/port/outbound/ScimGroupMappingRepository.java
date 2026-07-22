package br.com.brew.brassia.security.application.port.outbound;

import java.util.Optional;
import java.util.UUID;

public interface ScimGroupMappingRepository {
    record Mapping(UUID securityGroupId, boolean active) {}

    Optional<Mapping> findActive(UUID providerId, String externalGroupId);
    void create(UUID providerId, String externalGroupId, UUID securityGroupId);
}
