package br.com.brew.brassia.security.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Fator MFA (TOTP nesta fatia). Segredo fica cifrado na persistência. */
public final class MfaFactor {
    public enum Type { TOTP }
    public enum Status { PENDING, ACTIVE, REVOKED }

    private final UUID id;
    private final UserId userId;
    private final Type type;
    private final String label;
    private Status status;
    private final String secretCiphertext;
    private final int secretKeyVersion;
    private Instant confirmedAt;
    private final Instant createdAt;

    private MfaFactor(UUID id, UserId userId, Type type, String label, Status status,
            String secretCiphertext, int secretKeyVersion, Instant confirmedAt, Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.type = Objects.requireNonNull(type);
        this.label = Objects.requireNonNull(label);
        this.status = Objects.requireNonNull(status);
        this.secretCiphertext = Objects.requireNonNull(secretCiphertext);
        this.secretKeyVersion = secretKeyVersion;
        this.confirmedAt = confirmedAt;
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static MfaFactor pendingTotp(UserId userId, String secretCiphertext, int keyVersion, Instant now) {
        return new MfaFactor(UUID.randomUUID(), userId, Type.TOTP, "Authenticator", Status.PENDING,
                secretCiphertext, keyVersion, null, now);
    }

    public static MfaFactor reconstitute(UUID id, UserId userId, Type type, String label, Status status,
            String secretCiphertext, int secretKeyVersion, Instant confirmedAt, Instant createdAt) {
        return new MfaFactor(id, userId, type, label, status, secretCiphertext, secretKeyVersion, confirmedAt, createdAt);
    }

    public void confirm(Instant now) {
        if (status != Status.PENDING) {
            throw new IllegalStateException("fator não está pendente");
        }
        status = Status.ACTIVE;
        confirmedAt = now;
    }

    public void revoke() {
        status = Status.REVOKED;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public UUID id() { return id; }
    public UserId userId() { return userId; }
    public Type type() { return type; }
    public String label() { return label; }
    public Status status() { return status; }
    public String secretCiphertext() { return secretCiphertext; }
    public int secretKeyVersion() { return secretKeyVersion; }
    public Instant confirmedAt() { return confirmedAt; }
    public Instant createdAt() { return createdAt; }
}
