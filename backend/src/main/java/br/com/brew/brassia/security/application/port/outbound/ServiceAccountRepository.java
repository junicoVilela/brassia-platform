package br.com.brew.brassia.security.application.port.outbound;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceAccountRepository {
    record ServiceAccountView(UUID id, UUID breweryId, String code, String name, boolean active) {}

    UUID create(UUID breweryId, String code, String name);
    List<ServiceAccountView> listByBrewery(UUID breweryId);
    Optional<ServiceAccountView> findById(UUID id);
    boolean belongsToBrewery(UUID id, UUID breweryId);
}
