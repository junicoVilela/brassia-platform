package br.com.brew.brassia.security.adapter.inbound.web.dto;

import br.com.brew.brassia.audit.AuditQuery;
import java.util.List;

/** Página de eventos de auditoria com filtros server-side (SEC-B03). */
public record AuditEventPageResponse(
        List<AuditQuery.AuditEntry> content, int page, int size, long totalElements, int totalPages) {

    public static AuditEventPageResponse from(AuditQuery.Page page) {
        return new AuditEventPageResponse(
                page.content(), page.page(), page.size(), page.totalElements(), page.totalPages());
    }
}
