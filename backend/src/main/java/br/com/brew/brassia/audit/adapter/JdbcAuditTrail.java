package br.com.brew.brassia.audit.adapter;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.shared.observability.SensitiveDataMasker;
import br.com.brew.brassia.shared.observability.Trace;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Persiste o evento de auditoria (append-only) em {@code audit_event}, com o
 * diff/metadados <em>mascarados</em> (nunca senha/token/segredo em claro) e o
 * traceId da requisição.
 */
@Component
public class JdbcAuditTrail implements AuditTrail {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final JdbcClient jdbcClient;

    JdbcAuditTrail(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void record(AuditEvent event) {
        var masked = SensitiveDataMasker.mask(event.metadata());
        jdbcClient.sql("""
                INSERT INTO audit_event
                    (id, brewery_id, actor_id, action, target_type, target_id, outcome, trace_id, change_summary, occurred_at)
                VALUES (:id, :breweryId, :actorId, :action, :targetType, :targetId, :outcome, :traceId,
                        CAST(:changeSummary AS jsonb), :occurredAt)
                """)
                .param("id", UUID.randomUUID())
                .param("breweryId", event.breweryId())
                .param("actorId", event.actorId())
                .param("action", event.action())
                .param("targetType", event.resourceType())
                .param("targetId", event.resourceId())
                .param("outcome", event.outcome().name())
                .param("traceId", Trace.currentTraceId())
                .param("changeSummary", toJson(masked))
                .param("occurredAt", java.sql.Timestamp.from(event.occurredAt()))
                .update();
    }

    static String toJson(Map<String, String> metadata) {
        try {
            return JSON.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
