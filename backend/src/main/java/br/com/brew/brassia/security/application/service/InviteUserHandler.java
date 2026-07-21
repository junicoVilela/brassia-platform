package br.com.brew.brassia.security.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.security.application.port.inbound.InviteUserUseCase;
import br.com.brew.brassia.security.application.port.outbound.AccountTokenRepository;
import br.com.brew.brassia.security.application.port.outbound.NotificationGateway;
import br.com.brew.brassia.security.application.port.outbound.SecurityUserRepository;
import br.com.brew.brassia.security.application.port.outbound.TokenHasher;
import br.com.brew.brassia.security.domain.AccountToken;
import br.com.brew.brassia.security.domain.DisplayName;
import br.com.brew.brassia.security.domain.EmailAddress;
import br.com.brew.brassia.security.domain.SecurityUser;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

/**
 * Convida um usuário interno: cria a conta em estado INVITED e um token de
 * convite de uso único (persistido por hash). O token bruto sai apenas pela
 * {@link NotificationGateway}; nunca pela resposta nem pela auditoria.
 */
public final class InviteUserHandler implements InviteUserUseCase {
    static final Duration INVITATION_TTL = Duration.ofDays(7);
    private static final int TOKEN_BYTES = 32; // 256 bits de entropia

    private final SecurityUserRepository users;
    private final AccountTokenRepository tokens;
    private final TokenHasher tokenHasher;
    private final NotificationGateway notifications;
    private final AuditTrail audit;
    private final SecureRandom random = new SecureRandom();

    public InviteUserHandler(
            SecurityUserRepository users,
            AccountTokenRepository tokens,
            TokenHasher tokenHasher,
            NotificationGateway notifications,
            AuditTrail audit) {
        this.users = Objects.requireNonNull(users);
        this.tokens = Objects.requireNonNull(tokens);
        this.tokenHasher = Objects.requireNonNull(tokenHasher);
        this.notifications = Objects.requireNonNull(notifications);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var email = new EmailAddress(command.email());
        var displayName = new DisplayName(command.displayName());

        if (users.existsByNormalizedEmail(email.normalized())) {
            throw new IllegalStateException("email already registered");
        }

        var user = SecurityUser.invite(email, displayName);
        var rawToken = generateRawToken();
        var expiresAt = Instant.now().plus(INVITATION_TTL);
        var token = AccountToken.invitation(user.id(), tokenHasher.hash(rawToken), expiresAt);

        users.save(user);
        tokens.save(token);
        notifications.sendInvitation(email, rawToken, expiresAt);

        audit.record(AuditEvent.success(
                command.breweryId(),
                command.actorId(),
                "security.user.invite",
                "security_user",
                user.id().value().toString(),
                Map.of("email", email.value(), "status", user.status().name())));

        return new Result(user.id().value(), email.value(), user.status().name());
    }

    private String generateRawToken() {
        var bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
