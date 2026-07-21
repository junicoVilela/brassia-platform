package br.com.brew.brassia.security.adapter.outbound.persistence;

import br.com.brew.brassia.security.application.port.outbound.LoginEventRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcLoginEventRepository implements LoginEventRepository {
    private final JdbcClient jdbcClient;

    JdbcLoginEventRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void record(UUID userId, String identifier, Outcome outcome, String reasonCode,
            String ip, String userAgent, String traceId) {
        jdbcClient.sql("""
                INSERT INTO login_event
                    (id, user_id, attempted_identifier_hash, outcome, reason_code, ip_hash, user_agent_hash, trace_id)
                VALUES (:id, :userId, :identifierHash, :outcome, :reasonCode, :ipHash, :uaHash, :traceId)
                """)
                .param("id", UUID.randomUUID())
                .param("userId", userId)
                .param("identifierHash", sha256(identifier))
                .param("outcome", outcome.name())
                .param("reasonCode", reasonCode)
                .param("ipHash", sha256(ip))
                .param("uaHash", sha256(userAgent))
                .param("traceId", traceId)
                .update();
    }

    @Override
    public List<LoginEventView> recentByUser(UUID userId, int limit) {
        return jdbcClient.sql("""
                SELECT occurred_at, outcome, reason_code FROM login_event
                WHERE user_id = :userId
                ORDER BY occurred_at DESC
                LIMIT :limit
                """)
                .param("userId", userId)
                .param("limit", limit)
                .query((rs, n) -> new LoginEventView(
                        rs.getTimestamp("occurred_at").toInstant(),
                        rs.getString("outcome"),
                        rs.getString("reason_code")))
                .list();
    }

    private static String sha256(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
