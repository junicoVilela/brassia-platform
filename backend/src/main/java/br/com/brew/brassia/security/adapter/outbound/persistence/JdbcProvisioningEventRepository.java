package br.com.brew.brassia.security.adapter.outbound.persistence;

import br.com.brew.brassia.security.application.port.outbound.ProvisioningEventRepository;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcProvisioningEventRepository implements ProvisioningEventRepository {
    private final JdbcClient jdbc;

    JdbcProvisioningEventRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UUID log(UUID providerId, String externalId, String resourceType, String operation,
            String outcome, String idempotencyKey, String errorCode, String traceId) {
        var id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO provisioning_event (id, provider_id, external_id, resource_type, operation,
                    outcome, idempotency_key, error_code, trace_id)
                VALUES (:id, :providerId, :externalId, :resourceType, :operation, :outcome,
                    :idempotencyKey, :errorCode, :traceId)
                """)
                .param("id", id)
                .param("providerId", providerId)
                .param("externalId", externalId)
                .param("resourceType", resourceType)
                .param("operation", operation)
                .param("outcome", outcome)
                .param("idempotencyKey", idempotencyKey)
                .param("errorCode", errorCode)
                .param("traceId", traceId)
                .update();
        return id;
    }

    @Override
    public boolean existsByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return false;
        }
        return jdbc.sql("SELECT count(*) FROM provisioning_event WHERE idempotency_key = :key")
                .param("key", idempotencyKey)
                .query(Long.class).single() > 0;
    }
}
