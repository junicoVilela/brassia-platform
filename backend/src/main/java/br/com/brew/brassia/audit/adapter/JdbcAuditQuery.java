package br.com.brew.brassia.audit.adapter;

import br.com.brew.brassia.audit.AuditQuery;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class JdbcAuditQuery implements AuditQuery {
    private final JdbcClient jdbcClient;

    JdbcAuditQuery(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public List<AuditEntry> recent(UUID breweryId, int limit) {
        return jdbcClient.sql("""
                SELECT occurred_at, action, outcome, target_type, target_id, actor_id, change_summary::text AS change_summary
                FROM audit_event
                WHERE brewery_id = :breweryId
                ORDER BY occurred_at DESC
                LIMIT :limit
                """)
                .param("breweryId", breweryId)
                .param("limit", limit)
                .query((rs, n) -> new AuditEntry(
                        rs.getTimestamp("occurred_at").toInstant(),
                        rs.getString("action"),
                        rs.getString("outcome"),
                        rs.getString("target_type"),
                        rs.getString("target_id"),
                        rs.getObject("actor_id", UUID.class),
                        rs.getString("change_summary")))
                .list();
    }
}
