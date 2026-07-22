package br.com.brew.brassia.security.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.security.application.port.inbound.ConfirmEmailVerificationUseCase;
import br.com.brew.brassia.security.application.port.inbound.RequestEmailVerificationUseCase;
import br.com.brew.brassia.security.application.port.inbound.RequestPasswordResetUseCase;
import br.com.brew.brassia.security.application.port.inbound.ResetPasswordUseCase;
import br.com.brew.brassia.security.application.port.outbound.AccountTokenRepository;
import br.com.brew.brassia.security.application.port.outbound.NotificationGateway;
import br.com.brew.brassia.security.application.port.outbound.PasswordCredentialRepository;
import br.com.brew.brassia.security.application.port.outbound.PasswordHasher;
import br.com.brew.brassia.security.application.port.outbound.SecurityUserRepository;
import br.com.brew.brassia.security.application.port.outbound.TokenHasher;
import br.com.brew.brassia.security.application.port.outbound.UserSessionRegistry;
import br.com.brew.brassia.security.domain.AccountStatus;
import br.com.brew.brassia.security.domain.AccountToken;
import br.com.brew.brassia.security.domain.PasswordCredential;
import br.com.brew.brassia.security.domain.RawPassword;
import br.com.brew.brassia.security.domain.UserId;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Recuperação de senha e verificação de e-mail (SEC-010). */
public final class AccountRecoveryHandler {
    static final Duration PASSWORD_RESET_TTL = Duration.ofHours(1);
    static final Duration EMAIL_VERIFICATION_TTL = Duration.ofDays(3);
    private static final int TOKEN_BYTES = 32;

    private final SecurityUserRepository users;
    private final AccountTokenRepository tokens;
    private final TokenHasher tokenHasher;
    private final NotificationGateway notifications;
    private final PasswordCredentialRepository credentials;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicy passwordPolicy;
    private final UserSessionRegistry sessions;
    private final AuditTrail audit;
    private final SecureRandom random = new SecureRandom();

    public AccountRecoveryHandler(
            SecurityUserRepository users,
            AccountTokenRepository tokens,
            TokenHasher tokenHasher,
            NotificationGateway notifications,
            PasswordCredentialRepository credentials,
            PasswordHasher passwordHasher,
            PasswordPolicy passwordPolicy,
            UserSessionRegistry sessions,
            AuditTrail audit) {
        this.users = Objects.requireNonNull(users);
        this.tokens = Objects.requireNonNull(tokens);
        this.tokenHasher = Objects.requireNonNull(tokenHasher);
        this.notifications = Objects.requireNonNull(notifications);
        this.credentials = Objects.requireNonNull(credentials);
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
        this.passwordPolicy = Objects.requireNonNull(passwordPolicy);
        this.sessions = Objects.requireNonNull(sessions);
        this.audit = Objects.requireNonNull(audit);
    }

    public void requestPasswordReset(RequestPasswordResetUseCase.Command command) {
        var normalized = normalize(command.email());
        users.findByNormalizedEmail(normalized).ifPresent(user -> {
            if (user.status() != AccountStatus.ACTIVE) {
                return;
            }
            var rawToken = generateRawToken();
            var expires = Instant.now().plus(PASSWORD_RESET_TTL);
            tokens.save(AccountToken.passwordReset(user.id(), tokenHasher.hash(rawToken), expires));
            notifications.sendPasswordReset(user.email(), rawToken, expires);
            audit.record(AuditEvent.success(null, user.id().value(), "security.password.reset.request",
                    "security_user", user.id().value().toString(), Map.of()));
        });
    }

    public void resetPassword(ResetPasswordUseCase.Command command) {
        var hash = tokenHasher.hash(command.rawToken());
        var token = tokens.findByHashAndType(hash, AccountToken.Type.PASSWORD_RESET)
                .orElseThrow(() -> new IllegalArgumentException("token inválido"));
        var now = Instant.now();
        token.consume(now);
        tokens.save(token);
        var userId = token.userId();
        var newPassword = new RawPassword(command.newPassword());
        passwordPolicy.validate(newPassword);
        credentials.save(new PasswordCredential(userId, passwordHasher.hash(newPassword.value()),
                ChangePasswordHandler.ENCODER_ID));
        sessions.revokeAll(userId);
        audit.record(AuditEvent.success(null, userId.value(), "security.password.reset.complete",
                "security_user", userId.value().toString(), Map.of()));
    }

    public void requestEmailVerification(RequestEmailVerificationUseCase.Command command) {
        var userId = new UserId(command.userId());
        var user = users.findById(userId).orElseThrow(() -> new IllegalArgumentException("usuário inválido"));
        if (user.emailVerifiedAt() != null) {
            return;
        }
        var rawToken = generateRawToken();
        var expires = Instant.now().plus(EMAIL_VERIFICATION_TTL);
        tokens.save(AccountToken.emailVerification(userId, tokenHasher.hash(rawToken), expires));
        notifications.sendEmailVerification(user.email(), rawToken, expires);
        audit.record(AuditEvent.success(null, command.userId(), "security.email.verification.request",
                "security_user", command.userId().toString(), Map.of()));
    }

    public void confirmEmailVerification(ConfirmEmailVerificationUseCase.Command command) {
        var hash = tokenHasher.hash(command.rawToken());
        var token = tokens.findByHashAndType(hash, AccountToken.Type.EMAIL_VERIFICATION)
                .orElseThrow(() -> new IllegalArgumentException("token inválido"));
        var now = Instant.now();
        token.consume(now);
        tokens.save(token);
        var user = users.findById(token.userId()).orElseThrow();
        user.verifyEmail(now);
        users.save(user);
        audit.record(AuditEvent.success(null, user.id().value(), "security.email.verification.confirm",
                "security_user", user.id().value().toString(), Map.of()));
    }

    private String generateRawToken() {
        var bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
