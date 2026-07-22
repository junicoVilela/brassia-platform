package br.com.brew.brassia.security.application.port.outbound;

import br.com.brew.brassia.security.domain.TemporaryAccessGrant;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistência das concessões de acesso temporário. */
public interface TemporaryAccessRepository {

    /** Permissão ativa do catálogo pelo código (id + se é crítica). */
    Optional<PermissionRef> permissionByCode(String code);

    /** Insere a concessão (pendente de aprovação) e devolve o id gerado. */
    UUID insert(NewGrant grant);

    /** Concessão pelo id, restrita à cervejaria (evita vazamento entre tenants). */
    Optional<TemporaryAccessGrant> findById(UUID id, UUID breweryId);

    void approve(UUID id, UUID approverId, Instant approvedAt);

    void revoke(UUID id, UUID revokedBy, Instant revokedAt);

    /** Concessões da cervejaria (mais recentes primeiro), para visão administrativa. */
    List<TemporaryAccessGrant> current(UUID breweryId);

    record PermissionRef(UUID id, boolean critical) {}

    record NewGrant(UUID breweryId, UUID userId, UUID permissionId, String reason,
            Instant validFrom, Instant validUntil, UUID requestedBy) {}
}
