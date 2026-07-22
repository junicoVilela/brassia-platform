package br.com.brew.brassia.security.application.port.inbound;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Visão administrativa das concessões temporárias da cervejaria ativa. */
public interface TemporaryAccessQuery {

    List<GrantView> current(UUID breweryId);

    /**
     * @param status situação derivada (PENDING/ACTIVE/EXPIRED/REVOKED)
     */
    record GrantView(UUID id, UUID userId, String permissionCode, boolean critical,
            String reason, Instant validFrom, Instant validUntil,
            UUID requestedBy, UUID approvedBy, String status) {}
}
