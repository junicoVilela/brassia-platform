package br.com.brew.brassia.security.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.security.application.port.inbound.AcceptInvitationUseCase;
import br.com.brew.brassia.security.application.port.outbound.AccountTokenRepository;
import br.com.brew.brassia.security.application.port.outbound.PasswordCredentialRepository;
import br.com.brew.brassia.security.application.port.outbound.PasswordHasher;
import br.com.brew.brassia.security.application.port.outbound.SecurityUserRepository;
import br.com.brew.brassia.security.application.port.outbound.TokenHasher;
import br.com.brew.brassia.security.domain.AccountToken;
import br.com.brew.brassia.security.domain.PasswordCredential;
import br.com.brew.brassia.security.domain.RawPassword;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Aceite de convite: valida o token, define a credencial de senha, verifica o
 * e-mail e ativa a conta (INVITED → ACTIVE), consumindo o token (uso único).
 * Mensagens de erro são genéricas para não permitir enumeração de tokens/contas.
 */
public final class AcceptInvitationHandler implements AcceptInvitationUseCase {
    static final String ENCODER_ID = "delegating";

    private final SecurityUserRepository users;
    private final AccountTokenRepository tokens;
    private final PasswordCredentialRepository credentials;
    private final TokenHasher tokenHasher;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicy passwordPolicy;
    private final AuditTrail audit;

    public AcceptInvitationHandler(
            SecurityUserRepository users,
            AccountTokenRepository tokens,
            PasswordCredentialRepository credentials,
            TokenHasher tokenHasher,
            PasswordHasher passwordHasher,
            PasswordPolicy passwordPolicy,
            AuditTrail audit) {
        this.users = Objects.requireNonNull(users);
        this.tokens = Objects.requireNonNull(tokens);
        this.credentials = Objects.requireNonNull(credentials);
        this.tokenHasher = Objects.requireNonNull(tokenHasher);
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
        this.passwordPolicy = Objects.requireNonNull(passwordPolicy);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var password = new RawPassword(command.password()); // valida antes de mutar
        passwordPolicy.validate(password); // rejeita senha comprometida
        var now = Instant.now();
        var token = tokens.findInvitationByHash(tokenHasher.hash(requireToken(command.rawToken())))
                .orElseThrow(AcceptInvitationHandler::invalidToken);
        if (token.type() != AccountToken.Type.INVITATION || token.usedAt() != null || token.isExpired(now)) {
            throw invalidToken();
        }

        var user = users.findById(token.userId()).orElseThrow(AcceptInvitationHandler::invalidToken);
        user.activateFromInvitation(now);
        token.consume(now);

        users.save(user);
        tokens.save(token);
        credentials.save(new PasswordCredential(user.id(), passwordHasher.hash(password.value()), ENCODER_ID));

        audit.record(AuditEvent.success(
                null,
                user.id().value(),
                "security.user.activate",
                "security_user",
                user.id().value().toString(),
                Map.of("status", user.status().name())));

        return new Result(user.id().value(), user.status().name());
    }

    private static String requireToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidToken();
        }
        return rawToken;
    }

    // Erro genérico (400) para token inválido/expirado/usado — evita enumeração.
    private static IllegalArgumentException invalidToken() {
        return new IllegalArgumentException("convite inválido");
    }
}
