package br.com.brew.brassia.security.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.security.application.port.inbound.ManageMembershipUseCase;
import br.com.brew.brassia.security.application.port.outbound.GroupMembershipRepository;
import br.com.brew.brassia.security.application.port.outbound.SecurityUserRepository;
import br.com.brew.brassia.security.domain.UserId;
import java.util.Map;
import java.util.Objects;

/**
 * Associa/desassocia usuário↔grupo na cervejaria ativa do ator. Grupo inativo/
 * inexistente e usuário inexistente são rejeitados; associação duplicada é conflito.
 */
public final class ManageMembershipHandler implements ManageMembershipUseCase {
    private final SecurityUserRepository users;
    private final GroupMembershipRepository memberships;
    private final AuditTrail audit;

    public ManageMembershipHandler(SecurityUserRepository users, GroupMembershipRepository memberships, AuditTrail audit) {
        this.users = Objects.requireNonNull(users);
        this.memberships = Objects.requireNonNull(memberships);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public void grant(Command command) {
        var targetUser = validate(command);
        if (memberships.hasActiveMembership(targetUser, command.groupId(), command.breweryId())) {
            throw new IllegalStateException("associação já existe");
        }
        memberships.addMembership(targetUser, command.groupId(), command.breweryId());
        record(command, "security.membership.grant");
    }

    @Override
    public void revoke(Command command) {
        var targetUser = validate(command);
        memberships.revokeMembership(targetUser, command.groupId(), command.breweryId());
        record(command, "security.membership.revoke");
    }

    private UserId validate(Command command) {
        if (!memberships.groupActiveById(command.groupId())) {
            throw new IllegalArgumentException("grupo inexistente ou inativo");
        }
        var targetUser = new UserId(command.targetUserId());
        if (users.findById(targetUser).isEmpty()) {
            throw new IllegalArgumentException("usuário inexistente");
        }
        return targetUser;
    }

    private void record(Command command, String action) {
        audit.record(AuditEvent.success(
                command.breweryId(),
                command.actorId(),
                action,
                "user_group_membership",
                command.targetUserId().toString(),
                Map.of("group", command.groupId().toString())));
    }
}
