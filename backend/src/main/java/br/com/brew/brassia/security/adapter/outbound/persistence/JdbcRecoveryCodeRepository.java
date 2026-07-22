package br.com.brew.brassia.security.adapter.outbound.persistence;

import br.com.brew.brassia.security.application.port.outbound.RecoveryCodeRepository;
import br.com.brew.brassia.security.domain.UserId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcRecoveryCodeRepository implements RecoveryCodeRepository {
    private final JdbcClient jdbc;

    JdbcRecoveryCodeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void replaceAll(UserId userId, List<String> codeHashes, Instant generatedAt) {
        jdbc.sql("DELETE FROM recovery_code WHERE user_id = :userId")
                .param("userId", userId.value())
                .update();
        for (var hash : codeHashes) {
            jdbc.sql("""
                    INSERT INTO recovery_code (id, user_id, code_hash, generated_at)
                    VALUES (:id, :userId, :hash, :generated)
                    """)
                    .param("id", UUID.randomUUID())
                    .param("userId", userId.value())
                    .param("hash", hash)
                    .param("generated", Timestamp.from(generatedAt))
                    .update();
        }
    }

    @Override
    public Optional<String> consumeByHash(UserId userId, String codeHash, Instant now) {
        var updated = jdbc.sql("""
                UPDATE recovery_code SET used_at = :now
                WHERE user_id = :userId AND code_hash = :hash AND used_at IS NULL
                RETURNING id
                """)
                .param("now", Timestamp.from(now))
                .param("userId", userId.value())
                .param("hash", codeHash)
                .query(UUID.class)
                .optional();
        return updated.map(id -> codeHash);
    }

    @Override
    public int countUnused(UserId userId) {
        return jdbc.sql("""
                SELECT count(*) FROM recovery_code
                WHERE user_id = :userId AND used_at IS NULL
                """)
                .param("userId", userId.value())
                .query(Integer.class)
                .single();
    }
}
