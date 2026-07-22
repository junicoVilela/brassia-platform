package br.com.brew.brassia.security.adapter.outbound.persistence;

import br.com.brew.brassia.security.application.port.outbound.LoginThrottleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcLoginThrottleRepository implements LoginThrottleRepository {
    private final JdbcClient jdbc;

    JdbcLoginThrottleRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ThrottleState> find(String subjectHash, SubjectType type) {
        return jdbc.sql("""
                SELECT failure_count, penalty_until FROM login_throttle
                WHERE subject_hash = :hash AND subject_type = :type
                """)
                .param("hash", subjectHash)
                .param("type", type.name())
                .query((rs, n) -> new ThrottleState(
                        rs.getInt("failure_count"),
                        rs.getTimestamp("penalty_until") == null ? null : rs.getTimestamp("penalty_until").toInstant()))
                .optional();
    }

    @Override
    public void recordFailure(String subjectHash, SubjectType type, int failureCount, Instant penaltyUntil) {
        jdbc.sql("""
                INSERT INTO login_throttle (subject_hash, subject_type, failure_count, penalty_until, last_failure_at)
                VALUES (:hash, :type, :count, :penalty, now())
                ON CONFLICT (subject_hash, subject_type) DO UPDATE SET
                    failure_count = EXCLUDED.failure_count,
                    penalty_until = EXCLUDED.penalty_until,
                    last_failure_at = now()
                """)
                .param("hash", subjectHash)
                .param("type", type.name())
                .param("count", failureCount)
                .param("penalty", penaltyUntil == null ? null : Timestamp.from(penaltyUntil))
                .update();
    }

    @Override
    public void reset(String subjectHash, SubjectType type) {
        jdbc.sql("DELETE FROM login_throttle WHERE subject_hash = :hash AND subject_type = :type")
                .param("hash", subjectHash)
                .param("type", type.name())
                .update();
    }
}
