package br.com.brew.brassia.audit.adapter;

import br.com.brew.brassia.audit.AuditQuery;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class JdbcAuditQuery implements AuditQuery {
    private static final String COLUMNS =
            "occurred_at, action, outcome, target_type, target_id, actor_id, change_summary::text AS change_summary";
    private static final int MAX_SIZE = 200;

    private final JdbcClient jdbcClient;

    JdbcAuditQuery(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Page search(SearchCriteria c) {
        var where = new StringBuilder("WHERE brewery_id = :breweryId");
        var params = new HashMap<String, Object>();
        params.put("breweryId", c.breweryId());
        appendLike(where, params, "action", c.action());
        appendLike(where, params, "target_type", c.targetType());
        if (c.actorId() != null) {
            where.append(" AND actor_id = :actorId");
            params.put("actorId", c.actorId());
        }
        if (c.outcome() != null && !c.outcome().isBlank()) {
            where.append(" AND outcome = :outcome");
            params.put("outcome", c.outcome().trim());
        }
        if (c.from() != null) {
            where.append(" AND occurred_at >= :from");
            params.put("from", Timestamp.from(c.from()));
        }
        if (c.to() != null) {
            where.append(" AND occurred_at <= :to");
            params.put("to", Timestamp.from(c.to()));
        }

        int size = c.size() <= 0 ? 20 : Math.min(c.size(), MAX_SIZE);
        int page = Math.max(c.page(), 0);

        long total = jdbcClient.sql("SELECT count(*) FROM audit_event " + where)
                .params(params).query(Long.class).single();

        var content = jdbcClient.sql("SELECT " + COLUMNS + " FROM audit_event " + where
                        + " ORDER BY occurred_at DESC LIMIT :limit OFFSET :offset")
                .params(params)
                .param("limit", size)
                .param("offset", (long) page * size)
                .query((rs, n) -> mapRow(rs))
                .list();

        int totalPages = (int) Math.ceil((double) total / size);
        return new Page(content, page, size, total, totalPages);
    }

    private static void appendLike(StringBuilder where, Map<String, Object> params, String column, String value) {
        if (value != null && !value.isBlank()) {
            where.append(" AND ").append(column).append(" ILIKE :").append(column);
            params.put(column, "%" + value.trim() + "%");
        }
    }

    private static AuditEntry mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AuditEntry(
                rs.getTimestamp("occurred_at").toInstant(),
                rs.getString("action"),
                rs.getString("outcome"),
                rs.getString("target_type"),
                rs.getString("target_id"),
                rs.getObject("actor_id", UUID.class),
                rs.getString("change_summary"));
    }

    @Override
    public List<AuditEntry> recent(UUID breweryId, int limit) {
        return jdbcClient.sql("SELECT " + COLUMNS + " FROM audit_event"
                        + " WHERE brewery_id = :breweryId ORDER BY occurred_at DESC LIMIT :limit")
                .param("breweryId", breweryId)
                .param("limit", limit)
                .query((rs, n) -> mapRow(rs))
                .list();
    }
}
