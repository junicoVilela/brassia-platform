package br.com.brew.brassia.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Consulta da trilha de auditoria persistida (exposta a outros módulos). */
public interface AuditQuery {
    /** Eventos mais recentes de uma cervejaria (mais novos primeiro). */
    List<AuditEntry> recent(UUID breweryId, int limit);

    /** Busca paginada com filtros opcionais (mais novos primeiro). */
    Page search(SearchCriteria criteria);

    record AuditEntry(Instant occurredAt, String action, String outcome, String targetType,
            String targetId, UUID actorId, String changeSummary) {}

    /** Filtros nulos/em branco são ignorados. Escopo sempre pela cervejaria. */
    record SearchCriteria(UUID breweryId, String action, String targetType, UUID actorId,
            String outcome, Instant from, Instant to, int page, int size) {}

    record Page(List<AuditEntry> content, int page, int size, long totalElements, int totalPages) {}
}
