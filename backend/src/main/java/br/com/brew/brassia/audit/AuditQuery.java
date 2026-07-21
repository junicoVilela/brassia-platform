package br.com.brew.brassia.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Consulta da trilha de auditoria persistida (exposta a outros módulos). */
public interface AuditQuery {
    /** Eventos mais recentes de uma cervejaria (mais novos primeiro). */
    List<AuditEntry> recent(UUID breweryId, int limit);

    record AuditEntry(Instant occurredAt, String action, String outcome, String targetType,
            String targetId, UUID actorId, String changeSummary) {}
}
