package br.com.brew.brassia.audit.adapter;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.shared.observability.SensitiveDataMasker;
import br.com.brew.brassia.shared.observability.Trace;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Adapter inicial da trilha de auditoria: emite uma linha estruturada e
 * append-only no logger dedicado {@code AUDIT}, com metadados sensíveis
 * mascarados e o {@code traceId} da requisição.
 */
@Component
public class LoggingAuditTrail implements AuditTrail {

    private static final Logger AUDIT = LoggerFactory.getLogger("AUDIT");
    private static final String DASH = "-";

    @Override
    public void record(AuditEvent event) {
        Map<String, String> maskedMetadata = SensitiveDataMasker.mask(event.metadata());
        AUDIT.info(
                "audit action={} outcome={} resourceType={} resourceId={} breweryId={} actorId={} traceId={} occurredAt={} metadata={}",
                event.action(),
                event.outcome(),
                event.resourceType(),
                orDash(event.resourceId()),
                orDash(asString(event.breweryId())),
                orDash(asString(event.actorId())),
                Trace.currentTraceId(),
                event.occurredAt(),
                maskedMetadata);
    }

    private static String asString(UUID id) {
        return id == null ? null : id.toString();
    }

    private static String orDash(String value) {
        return value == null ? DASH : value;
    }
}
