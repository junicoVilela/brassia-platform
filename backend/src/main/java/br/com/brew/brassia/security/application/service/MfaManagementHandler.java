package br.com.brew.brassia.security.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.security.application.port.inbound.CompleteMfaLoginUseCase;
import br.com.brew.brassia.security.application.port.inbound.ConfirmTotpUseCase;
import br.com.brew.brassia.security.application.port.inbound.DisableTotpUseCase;
import br.com.brew.brassia.security.application.port.inbound.EnrollTotpUseCase;
import br.com.brew.brassia.security.application.port.inbound.HasActiveMfaQuery;
import br.com.brew.brassia.security.application.port.inbound.RegenerateRecoveryCodesUseCase;
import br.com.brew.brassia.security.application.port.outbound.MfaFactorRepository;
import br.com.brew.brassia.security.application.port.outbound.MfaSecretCipher;
import br.com.brew.brassia.security.application.port.outbound.PasswordCredentialRepository;
import br.com.brew.brassia.security.application.port.outbound.PasswordHasher;
import br.com.brew.brassia.security.application.port.outbound.RecoveryCodeRepository;
import br.com.brew.brassia.security.application.port.outbound.SecurityUserRepository;
import br.com.brew.brassia.security.application.port.outbound.TokenHasher;
import br.com.brew.brassia.security.domain.MfaFactor;
import br.com.brew.brassia.security.domain.Totp;
import br.com.brew.brassia.security.domain.UserId;
import br.com.brew.brassia.shared.security.ForbiddenException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Gestão de MFA TOTP e códigos de recuperação (SEC-009). */
public final class MfaManagementHandler {
    private static final int RECOVERY_CODE_COUNT = 10;
    private static final String ISSUER = "BrassIA";

    private final MfaFactorRepository factors;
    private final RecoveryCodeRepository recoveryCodes;
    private final MfaSecretCipher cipher;
    private final SecurityUserRepository users;
    private final PasswordCredentialRepository credentials;
    private final PasswordHasher passwordHasher;
    private final TokenHasher tokenHasher;
    private final AuditTrail audit;

    public MfaManagementHandler(
            MfaFactorRepository factors,
            RecoveryCodeRepository recoveryCodes,
            MfaSecretCipher cipher,
            SecurityUserRepository users,
            PasswordCredentialRepository credentials,
            PasswordHasher passwordHasher,
            TokenHasher tokenHasher,
            AuditTrail audit) {
        this.factors = Objects.requireNonNull(factors);
        this.recoveryCodes = Objects.requireNonNull(recoveryCodes);
        this.cipher = Objects.requireNonNull(cipher);
        this.users = Objects.requireNonNull(users);
        this.credentials = Objects.requireNonNull(credentials);
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
        this.tokenHasher = Objects.requireNonNull(tokenHasher);
        this.audit = Objects.requireNonNull(audit);
    }

    public EnrollTotpUseCase.Result enroll(EnrollTotpUseCase.Command command) {
        var userId = new UserId(command.userId());
        var user = users.findById(userId).orElseThrow(() -> new IllegalArgumentException("usuário inválido"));
        if (factors.findActiveTotpByUserId(userId).isPresent()) {
            throw new IllegalStateException("TOTP já ativo");
        }
        factors.revokeAllTotp(userId);
        var secret = Totp.generateSecret();
        var encrypted = cipher.encrypt(secret);
        var now = Instant.now();
        var factor = MfaFactor.pendingTotp(userId, encrypted.ciphertext(), encrypted.keyVersion(), now);
        factors.save(factor);
        audit.record(AuditEvent.success(null, command.userId(), "security.mfa.totp.enroll",
                "mfa_factor", factor.id().toString(), Map.of()));
        var uri = Totp.buildOtpAuthUri(secret, user.email().value(), ISSUER);
        return new EnrollTotpUseCase.Result(secret, uri);
    }

    public void confirm(ConfirmTotpUseCase.Command command) {
        var userId = new UserId(command.userId());
        var factor = factors.findPendingTotpByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("nenhum TOTP pendente"));
        var secret = decryptSecret(factor);
        if (!Totp.verify(secret, command.code())) {
            throw new IllegalArgumentException("código inválido");
        }
        var now = Instant.now();
        factor.confirm(now);
        factors.save(factor);
        audit.record(AuditEvent.success(null, command.userId(), "security.mfa.totp.confirm",
                "mfa_factor", factor.id().toString(), Map.of()));
    }

    public void disable(DisableTotpUseCase.Command command) {
        var userId = new UserId(command.userId());
        if (!command.recentReauth()) {
            verifyPassword(userId, command.currentPassword());
        }
        if (factors.findActiveTotpByUserId(userId).isEmpty()) {
            throw new IllegalStateException("TOTP não está ativo");
        }
        factors.revokeAllTotp(userId);
        audit.record(AuditEvent.success(null, command.userId(), "security.mfa.totp.disable",
                "mfa_factor", command.userId().toString(), Map.of()));
    }

    public RegenerateRecoveryCodesUseCase.Result regenerate(RegenerateRecoveryCodesUseCase.Command command) {
        var userId = new UserId(command.userId());
        if (factors.findActiveTotpByUserId(userId).isEmpty()) {
            throw new IllegalStateException("MFA não está ativo");
        }
        var rawCodes = generateRecoveryCodes();
        var hashes = rawCodes.stream().map(tokenHasher::hash).toList();
        recoveryCodes.replaceAll(userId, hashes, Instant.now());
        audit.record(AuditEvent.success(null, command.userId(), "security.mfa.recovery.regenerate",
                "recovery_code", command.userId().toString(), Map.of("count", String.valueOf(RECOVERY_CODE_COUNT))));
        return new RegenerateRecoveryCodesUseCase.Result(rawCodes);
    }

    public boolean hasActiveTotp(UUID userId) {
        return factors.findActiveTotpByUserId(new UserId(userId)).isPresent();
    }

    public CompleteMfaLoginUseCase.Result completeMfaLogin(CompleteMfaLoginUseCase.Command command) {
        var userId = new UserId(command.userId());
        var user = users.findById(userId).orElseThrow(() -> new IllegalArgumentException("usuário inválido"));
        boolean verified = switch (command.method()) {
            case TOTP -> verifyTotp(userId, command.code());
            case RECOVERY_CODE -> verifyRecovery(userId, command.code());
        };
        if (!verified) {
            audit.record(new AuditEvent(Instant.now(), null, command.userId(), "security.mfa.login.failure",
                    "security_user", command.userId().toString(), br.com.brew.brassia.audit.AuditOutcome.FAILURE,
                    Map.of("method", command.method().name())));
            throw new IllegalArgumentException("código inválido");
        }
        audit.record(AuditEvent.success(null, command.userId(), "security.mfa.login.success",
                "security_user", command.userId().toString(), Map.of("method", command.method().name())));
        return new CompleteMfaLoginUseCase.Result(user.id().value(), user.displayName().value());
    }

    private boolean verifyTotp(UserId userId, String code) {
        var factor = factors.findActiveTotpByUserId(userId).orElse(null);
        if (factor == null) {
            return false;
        }
        return Totp.verify(decryptSecret(factor), code);
    }

    private boolean verifyRecovery(UserId userId, String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        var hash = tokenHasher.hash(code.trim());
        return recoveryCodes.consumeByHash(userId, hash, Instant.now()).isPresent();
    }

    private String decryptSecret(MfaFactor factor) {
        return cipher.decrypt(new MfaSecretCipher.EncryptedSecret(
                factor.secretCiphertext(), factor.secretKeyVersion()));
    }

    private void verifyPassword(UserId userId, String currentPassword) {
        if (currentPassword == null || currentPassword.isBlank()) {
            throw new ForbiddenException("reautenticação necessária");
        }
        var matches = credentials.findByUserId(userId)
                .map(c -> passwordHasher.matches(currentPassword, c.passwordHash()))
                .orElse(false);
        if (!matches) {
            throw new IllegalArgumentException("senha atual incorreta");
        }
    }

    private static List<String> generateRecoveryCodes() {
        var random = new SecureRandom();
        var codes = new ArrayList<String>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            var part1 = randomPart(random, 4);
            var part2 = randomPart(random, 4);
            codes.add(part1 + "-" + part2);
        }
        return codes;
    }

    private static String randomPart(SecureRandom random, int length) {
        var chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        var sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
