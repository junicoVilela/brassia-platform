package br.com.brew.brassia.security.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.security.application.port.inbound.AdministerAccountUseCase;
import br.com.brew.brassia.security.application.port.outbound.SecurityUserRepository;
import br.com.brew.brassia.security.application.port.outbound.UserSessionRegistry;
import br.com.brew.brassia.security.domain.SecurityUser;
import br.com.brew.brassia.security.domain.UserId;
import java.util.Map;
import java.util.Objects;

/**
 * Aplica uma transição de ciclo da conta (bloquear/desbloquear/desativar) sobre
 * a conta alvo. A desativação revoga as sessões ativas do usuário.
 */
public final class AdministerAccountHandler implements AdministerAccountUseCase {
    private final SecurityUserRepository users;
    private final UserSessionRegistry sessions;
    private final AuditTrail audit;

    public AdministerAccountHandler(
            SecurityUserRepository users, UserSessionRegistry sessions, AuditTrail audit) {
        this.users = Objects.requireNonNull(users);
        this.sessions = Objects.requireNonNull(sessions);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var userId = new UserId(command.targetUserId());
        var user = users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("conta não encontrada"));

        applyTransition(user, command.operation());
        users.save(user);
        if (command.operation() == Operation.DISABLE) {
            sessions.revokeAll(userId);
        }

        audit.record(AuditEvent.success(
                command.breweryId(),
                command.actorId(),
                auditAction(command.operation()),
                "security_user",
                userId.value().toString(),
                Map.of("status", user.status().name())));

        return new Result(userId.value(), user.status().name());
    }

    private static void applyTransition(SecurityUser user, Operation operation) {
        switch (operation) {
            case BLOCK -> user.block();
            case UNBLOCK -> user.unblock();
            case DISABLE -> user.disable();
        }
    }

    private static String auditAction(Operation operation) {
        return switch (operation) {
            case BLOCK -> "security.user.block";
            case UNBLOCK -> "security.user.unblock";
            case DISABLE -> "security.user.disable";
        };
    }
}
