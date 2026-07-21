package br.com.brew.brassia.security.adapter.outbound.persistence;

import br.com.brew.brassia.security.application.port.outbound.PasswordHistoryRepository;
import br.com.brew.brassia.security.domain.PasswordCredential;
import br.com.brew.brassia.security.domain.UserId;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcPasswordHistoryRepository implements PasswordHistoryRepository {
    private final JdbcClient jdbcClient;

    JdbcPasswordHistoryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void save(PasswordCredential replaced) {
        jdbcClient.sql("""
                INSERT INTO password_history (id, user_id, password_hash, encoder_id)
                VALUES (:id, :userId, :hash, :encoderId)
                """)
                .param("id", UUID.randomUUID())
                .param("userId", replaced.userId().value())
                .param("hash", replaced.passwordHash())
                .param("encoderId", replaced.encoderId())
                .update();
    }

    @Override
    public List<String> recentHashes(UserId userId, int limit) {
        return jdbcClient.sql("""
                SELECT password_hash FROM password_history
                WHERE user_id = :userId
                ORDER BY replaced_at DESC
                LIMIT :limit
                """)
                .param("userId", userId.value())
                .param("limit", limit)
                .query(String.class).list();
    }
}
