package br.com.brew.brassia.security.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Token de conta (convite/verificação/reset). Guarda apenas o <em>hash</em> do
 * valor bruto; o valor legível nunca é persistido. É de uso único: uma vez
 * consumido, não pode ser reutilizado.
 */
public final class AccountToken {
    public enum Type { INVITATION, EMAIL_VERIFICATION, PASSWORD_RESET }

    private final UUID id;
    private final UserId userId;
    private final Type type;
    private final String tokenHash;
    private final Instant expiresAt;
    private Instant usedAt;

    private AccountToken(UUID id, UserId userId, Type type, String tokenHash, Instant expiresAt) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.type = Objects.requireNonNull(type);
        this.tokenHash = requireHash(tokenHash);
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    /** Token de convite com hash e expiração futura. */
    public static AccountToken invitation(UserId userId, String tokenHash, Instant expiresAt) {
        return new AccountToken(UUID.randomUUID(), userId, Type.INVITATION, tokenHash, expiresAt);
    }

    public static AccountToken passwordReset(UserId userId, String tokenHash, Instant expiresAt) {
        return new AccountToken(UUID.randomUUID(), userId, Type.PASSWORD_RESET, tokenHash, expiresAt);
    }

    public static AccountToken emailVerification(UserId userId, String tokenHash, Instant expiresAt) {
        return new AccountToken(UUID.randomUUID(), userId, Type.EMAIL_VERIFICATION, tokenHash, expiresAt);
    }

    /** Reconstrói o token a partir da persistência. */
    public static AccountToken reconstitute(UUID id, UserId userId, Type type,
            String tokenHash, Instant expiresAt, Instant usedAt) {
        var token = new AccountToken(id, userId, type, tokenHash, expiresAt);
        token.usedAt = usedAt;
        return token;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /** Marca o token como consumido; barra reuso e uso após expirar. */
    public void consume(Instant now) {
        if (usedAt != null) {
            throw new IllegalStateException("token já utilizado");
        }
        if (isExpired(now)) {
            throw new IllegalStateException("token expirado");
        }
        this.usedAt = now;
    }

    public UUID id() { return id; }
    public UserId userId() { return userId; }
    public Type type() { return type; }
    public String tokenHash() { return tokenHash; }
    public Instant expiresAt() { return expiresAt; }
    public Instant usedAt() { return usedAt; }

    private static String requireHash(String hash) {
        if (hash == null || hash.isBlank()) {
            throw new IllegalArgumentException("token hash é obrigatório");
        }
        return hash;
    }
}
