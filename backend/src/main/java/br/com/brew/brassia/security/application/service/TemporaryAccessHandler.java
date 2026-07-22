package br.com.brew.brassia.security.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.security.application.port.inbound.TemporaryAccessQuery;
import br.com.brew.brassia.security.application.port.inbound.TemporaryAccessUseCase;
import br.com.brew.brassia.security.application.port.outbound.SecurityUserRepository;
import br.com.brew.brassia.security.application.port.outbound.TemporaryAccessRepository;
import br.com.brew.brassia.security.application.port.outbound.TemporaryAccessRepository.NewGrant;
import br.com.brew.brassia.security.domain.TemporaryAccessGrant;
import br.com.brew.brassia.security.domain.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;

/**
 * Concede/aprova/revoga acesso temporário. Permissão comum vige na janela ao ser
 * solicitada; permissão crítica só vige após aprovação de um segundo usuário
 * (≠ solicitante). Todas as operações são auditadas.
 */
public final class TemporaryAccessHandler implements TemporaryAccessUseCase, TemporaryAccessQuery {

    private static final String RESOURCE = "temporary_access_grant";

    private final TemporaryAccessRepository grants;
    private final SecurityUserRepository users;
    private final AuditTrail audit;

    public TemporaryAccessHandler(TemporaryAccessRepository grants, SecurityUserRepository users, AuditTrail audit) {
        this.grants = Objects.requireNonNull(grants);
        this.users = Objects.requireNonNull(users);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public UUID request(RequestCommand command) {
        if (command.durationHours() <= 0) {
            throw new IllegalArgumentException("duração inválida");
        }
        var permission = grants.permissionByCode(command.permissionCode())
                .orElseThrow(() -> new IllegalArgumentException("permissão inexistente ou inativa"));
        if (users.findById(new UserId(command.targetUserId())).isEmpty()) {
            throw new IllegalArgumentException("usuário inexistente");
        }
        var now = Instant.now();
        var validUntil = now.plus(Duration.ofHours(command.durationHours()));
        var id = grants.insert(new NewGrant(command.breweryId(), command.targetUserId(), permission.id(),
                command.reason(), now, validUntil, command.actorId()));
        record(command.breweryId(), command.actorId(), "security.temporary-access.request", id,
                Map.of("permission", command.permissionCode(), "user", command.targetUserId().toString(),
                        "validUntil", validUntil.toString()));
        return id;
    }

    @Override
    public void approve(UUID grantId, UUID actorId, UUID breweryId) {
        var grant = load(grantId, breweryId);
        var now = Instant.now();
        if (grant.isRequestedBy(actorId)) {
            throw new AccessDeniedException("aprovador não pode ser o solicitante");
        }
        if (grant.isRevoked() || grant.isExpiredAt(now) || grant.isApproved()) {
            throw new IllegalStateException("concessão não está pendente");
        }
        grants.approve(grantId, actorId, now);
        record(breweryId, actorId, "security.temporary-access.approve", grantId,
                Map.of("user", grant.userId().toString(), "permission", grant.permissionCode()));
    }

    @Override
    public void revoke(UUID grantId, UUID actorId, UUID breweryId) {
        var grant = load(grantId, breweryId);
        if (grant.isRevoked()) {
            throw new IllegalStateException("concessão já revogada");
        }
        grants.revoke(grantId, actorId, Instant.now());
        record(breweryId, actorId, "security.temporary-access.revoke", grantId,
                Map.of("user", grant.userId().toString(), "permission", grant.permissionCode()));
    }

    @Override
    public List<GrantView> current(UUID breweryId) {
        var now = Instant.now();
        return grants.current(breweryId).stream().map(g -> toView(g, now)).toList();
    }

    private TemporaryAccessGrant load(UUID grantId, UUID breweryId) {
        return grants.findById(grantId, breweryId)
                .orElseThrow(() -> new IllegalArgumentException("concessão inexistente"));
    }

    private void record(UUID breweryId, UUID actorId, String action, UUID grantId, Map<String, String> metadata) {
        audit.record(AuditEvent.success(breweryId, actorId, action, RESOURCE, grantId.toString(), metadata));
    }

    private static GrantView toView(TemporaryAccessGrant g, Instant now) {
        return new GrantView(g.id(), g.userId(), g.permissionCode(), g.permissionCritical(), g.reason(),
                g.validFrom(), g.validUntil(), g.requestedBy(), g.approvedBy(), g.statusAt(now).name());
    }
}
