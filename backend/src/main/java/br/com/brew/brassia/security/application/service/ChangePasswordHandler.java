package br.com.brew.brassia.security.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.security.application.port.inbound.ChangePasswordUseCase;
import br.com.brew.brassia.security.application.port.outbound.PasswordCredentialRepository;
import br.com.brew.brassia.security.application.port.outbound.PasswordHasher;
import br.com.brew.brassia.security.application.port.outbound.PasswordHistoryRepository;
import br.com.brew.brassia.security.domain.PasswordCredential;
import br.com.brew.brassia.security.domain.RawPassword;
import br.com.brew.brassia.security.domain.UserId;
import java.util.Map;
import java.util.Objects;

/**
 * Troca a própria senha: verifica a atual, valida a nova pela política e barra a
 * reutilização das últimas N. Grava a nova credencial e arquiva a antiga no
 * histórico. Senha nunca é registrada.
 */
public final class ChangePasswordHandler implements ChangePasswordUseCase {
    static final String ENCODER_ID = "delegating";

    private final PasswordCredentialRepository credentials;
    private final PasswordHistoryRepository history;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicy passwordPolicy;
    private final AuditTrail audit;
    private final int historySize;

    public ChangePasswordHandler(
            PasswordCredentialRepository credentials,
            PasswordHistoryRepository history,
            PasswordHasher passwordHasher,
            PasswordPolicy passwordPolicy,
            AuditTrail audit,
            int historySize) {
        this.credentials = Objects.requireNonNull(credentials);
        this.history = Objects.requireNonNull(history);
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
        this.passwordPolicy = Objects.requireNonNull(passwordPolicy);
        this.audit = Objects.requireNonNull(audit);
        this.historySize = historySize;
    }

    @Override
    public void handle(Command command) {
        var userId = new UserId(command.userId());
        var current = credentials.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("conta sem credencial"));

        if (!passwordHasher.matches(command.currentPassword(), current.passwordHash())) {
            throw new IllegalArgumentException("senha atual incorreta");
        }

        var newPassword = new RawPassword(command.newPassword());
        passwordPolicy.validate(newPassword);
        rejectReuse(userId, newPassword, current);

        var updated = new PasswordCredential(userId, passwordHasher.hash(newPassword.value()), ENCODER_ID);
        credentials.save(updated);
        history.save(current); // arquiva a senha substituída

        audit.record(AuditEvent.success(null, command.userId(), "security.password.change",
                "security_user", command.userId().toString(), Map.of()));
    }

    private void rejectReuse(UserId userId, RawPassword candidate, PasswordCredential current) {
        if (passwordHasher.matches(candidate.value(), current.passwordHash())) {
            throw new IllegalArgumentException("não reutilize a senha atual");
        }
        var recent = history.recentHashes(userId, historySize);
        if (recent.stream().anyMatch(hash -> passwordHasher.matches(candidate.value(), hash))) {
            throw new IllegalArgumentException("não reutilize uma senha recente");
        }
    }
}
