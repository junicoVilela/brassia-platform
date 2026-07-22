package br.com.brew.brassia.security.adapter.outbound.persistence;

import br.com.brew.brassia.security.application.port.outbound.MfaFactorRepository;
import br.com.brew.brassia.security.domain.MfaFactor;
import br.com.brew.brassia.security.domain.UserId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcMfaFactorRepository implements MfaFactorRepository {
    private final JdbcClient jdbc;

    JdbcMfaFactorRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(MfaFactor factor) {
        jdbc.sql("""
                INSERT INTO mfa_factor (id, user_id, factor_type, label, status,
                    secret_ciphertext, secret_key_version, confirmed_at, created_at)
                VALUES (:id, :userId, :type, :label, :status, :secret, :keyVersion, :confirmed, :created)
                ON CONFLICT (user_id, factor_type) DO UPDATE SET
                    label = EXCLUDED.label, status = EXCLUDED.status,
                    secret_ciphertext = EXCLUDED.secret_ciphertext,
                    secret_key_version = EXCLUDED.secret_key_version,
                    confirmed_at = EXCLUDED.confirmed_at
                """)
                .param("id", factor.id())
                .param("userId", factor.userId().value())
                .param("type", factor.type().name())
                .param("label", factor.label())
                .param("status", factor.status().name())
                .param("secret", factor.secretCiphertext())
                .param("keyVersion", factor.secretKeyVersion())
                .param("confirmed", factor.confirmedAt() == null ? null : Timestamp.from(factor.confirmedAt()))
                .param("created", Timestamp.from(factor.createdAt()))
                .update();
    }

    @Override
    public Optional<MfaFactor> findActiveTotpByUserId(UserId userId) {
        return findByUserAndStatus(userId, MfaFactor.Status.ACTIVE);
    }

    @Override
    public Optional<MfaFactor> findPendingTotpByUserId(UserId userId) {
        return findByUserAndStatus(userId, MfaFactor.Status.PENDING);
    }

    @Override
    public void revokeAllTotp(UserId userId) {
        jdbc.sql("""
                UPDATE mfa_factor SET status = 'REVOKED'
                WHERE user_id = :userId AND factor_type = 'TOTP' AND status <> 'REVOKED'
                """)
                .param("userId", userId.value())
                .update();
    }

    private Optional<MfaFactor> findByUserAndStatus(UserId userId, MfaFactor.Status status) {
        return jdbc.sql("""
                SELECT id, user_id, factor_type, label, status, secret_ciphertext,
                       secret_key_version, confirmed_at, created_at
                FROM mfa_factor
                WHERE user_id = :userId AND factor_type = 'TOTP' AND status = :status
                """)
                .param("userId", userId.value())
                .param("status", status.name())
                .query((rs, n) -> MfaFactor.reconstitute(
                        rs.getObject("id", java.util.UUID.class),
                        new UserId(rs.getObject("user_id", java.util.UUID.class)),
                        MfaFactor.Type.valueOf(rs.getString("factor_type")),
                        rs.getString("label"),
                        MfaFactor.Status.valueOf(rs.getString("status")),
                        rs.getString("secret_ciphertext"),
                        rs.getInt("secret_key_version"),
                        rs.getTimestamp("confirmed_at") == null ? null : rs.getTimestamp("confirmed_at").toInstant(),
                        rs.getTimestamp("created_at").toInstant()))
                .optional();
    }
}
