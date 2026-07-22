package br.com.brew.brassia.security.application.port.outbound;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface FederationProviderRepository {
    record ProviderView(
            UUID id, UUID breweryId, String code, String displayName, String protocol,
            String status, String issuerOrEntityId, String metadataUri, Map<String, Object> configuration,
            boolean jitMode, long version) {}

    UUID create(UUID breweryId, String code, String displayName, String protocol,
            String issuerOrEntityId, Map<String, Object> configuration);
    Optional<ProviderView> findById(UUID id);
    List<ProviderView> listByBrewery(UUID breweryId);
    void update(UUID id, String displayName, String status, Map<String, Object> configuration, long version);
}
