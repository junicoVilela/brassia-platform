package br.com.brew.brassia.security.application.port.outbound;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface SecurityAlertRepository {
    record AlertView(
            UUID id, UUID breweryId, UUID userId, String alertType, String severity,
            String status, Map<String, Object> evidence, Instant createdAt) {}

    UUID create(UUID breweryId, UUID userId, String alertType, String severity, Map<String, Object> evidence);
    List<AlertView> listByBrewery(UUID breweryId, String status, int limit);
    Optional<AlertView> findById(UUID id);
    void updateStatus(UUID id, String status, UUID resolvedBy);
}
