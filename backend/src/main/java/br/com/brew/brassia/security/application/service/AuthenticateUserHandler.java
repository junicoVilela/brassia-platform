package br.com.brew.brassia.security.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditOutcome;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.security.application.port.inbound.AuthenticateUserUseCase;
import br.com.brew.brassia.security.application.port.outbound.EffectivePermissionsRepository;
import br.com.brew.brassia.security.application.port.outbound.PasswordCredentialRepository;
import br.com.brew.brassia.security.application.port.outbound.PasswordHasher;
import br.com.brew.brassia.security.application.port.outbound.SecurityUserRepository;
import br.com.brew.brassia.security.domain.AccountStatus;
import br.com.brew.brassia.security.domain.SecurityUser;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Autentica credenciais internas. A falha é sempre <em>genérica</em>
 * (mesma exceção para e-mail inexistente, conta não-ativa ou senha errada) para
 * não vazar quais contas existem. Auditadas sucesso e falha; a senha nunca é
 * registrada.
 */
public final class AuthenticateUserHandler implements AuthenticateUserUseCase {
    private final SecurityUserRepository users;
    private final PasswordCredentialRepository credentials;
    private final EffectivePermissionsRepository permissions;
    private final PasswordHasher passwordHasher;
    private final AuditTrail audit;

    public AuthenticateUserHandler(
            SecurityUserRepository users,
            PasswordCredentialRepository credentials,
            EffectivePermissionsRepository permissions,
            PasswordHasher passwordHasher,
            AuditTrail audit) {
        this.users = Objects.requireNonNull(users);
        this.credentials = Objects.requireNonNull(credentials);
        this.permissions = Objects.requireNonNull(permissions);
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var normalizedEmail = normalize(command.email());
        var user = users.findByNormalizedEmail(normalizedEmail).orElse(null);

        if (user == null || user.status() != AccountStatus.ACTIVE || !passwordMatches(user, command.password())) {
            recordFailure(user, normalizedEmail);
            throw new IllegalArgumentException("credenciais inválidas");
        }

        recordSuccess(user);
        return new Result(user.id().value(), user.displayName().value(), user.email().value(),
                permissions.findByUserId(user.id()));
    }

    private boolean passwordMatches(SecurityUser user, String rawPassword) {
        if (rawPassword == null) {
            return false;
        }
        return credentials.findByUserId(user.id())
                .map(c -> passwordHasher.matches(rawPassword, c.passwordHash()))
                .orElse(false);
    }

    private void recordSuccess(SecurityUser user) {
        audit.record(AuditEvent.success(null, user.id().value(), "security.login.success",
                "security_user", user.id().value().toString(), Map.of()));
    }

    private void recordFailure(SecurityUser user, String normalizedEmail) {
        var actorId = user == null ? null : user.id().value();
        audit.record(new AuditEvent(Instant.now(), null, actorId, "security.login.failure",
                "security_user", actorId == null ? null : actorId.toString(),
                AuditOutcome.FAILURE, Map.of("email", normalizedEmail)));
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
