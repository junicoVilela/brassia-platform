package br.com.brew.brassia.security.application.port.outbound;

import java.util.Optional;
import java.util.UUID;

public interface ProvisioningEventRepository {
    UUID log(UUID providerId, String externalId, String resourceType, String operation,
            String outcome, String idempotencyKey, String errorCode, String traceId);
    boolean existsByIdempotencyKey(String idempotencyKey);
}
