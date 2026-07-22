package br.com.brew.brassia.security.adapter.outbound.persistence;

import br.com.brew.brassia.security.application.port.outbound.TemporaryAccessRepository;
import br.com.brew.brassia.security.domain.TemporaryAccessGrant;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTemporaryAccessRepository implements TemporaryAccessRepository {

    private static final String SELECT = """
            SELECT t.id, t.brewery_id, t.user_id, t.permission_id, p.code, p.critical, t.reason,
                   t.valid_from, t.valid_until, t.requested_by, t.approved_by, t.revoked_at
            FROM temporary_access_grant t
            JOIN security_permission p ON p.id = t.permission_id
            """;

    private final JdbcClient jdbcClient;

    JdbcTemporaryAccessRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<PermissionRef> permissionByCode(String code) {
        return jdbcClient.sql("SELECT id, critical FROM security_permission WHERE code = :code AND active")
                .param("code", code)
                .query((rs, n) -> new PermissionRef(rs.getObject("id", UUID.class), rs.getBoolean("critical")))
                .optional();
    }

    @Override
    public UUID insert(NewGrant grant) {
        var id = UUID.randomUUID();
        jdbcClient.sql("""
                INSERT INTO temporary_access_grant
                    (id, brewery_id, user_id, permission_id, reason, valid_from, valid_until, requested_by)
                VALUES (:id, :breweryId, :userId, :permissionId, :reason, :validFrom, :validUntil, :requestedBy)
                """)
                .param("id", id)
                .param("breweryId", grant.breweryId())
                .param("userId", grant.userId())
                .param("permissionId", grant.permissionId())
                .param("reason", grant.reason())
                .param("validFrom", Timestamp.from(grant.validFrom()))
                .param("validUntil", Timestamp.from(grant.validUntil()))
                .param("requestedBy", grant.requestedBy())
                .update();
        return id;
    }

    @Override
    public Optional<TemporaryAccessGrant> findById(UUID id, UUID breweryId) {
        return jdbcClient.sql(SELECT + " WHERE t.id = :id AND t.brewery_id = :breweryId")
                .param("id", id)
                .param("breweryId", breweryId)
                .query(JdbcTemporaryAccessRepository::map)
                .optional();
    }

    @Override
    public void approve(UUID id, UUID approverId, Instant approvedAt) {
        jdbcClient.sql("""
                UPDATE temporary_access_grant SET approved_by = :approverId, approved_at = :approvedAt
                WHERE id = :id AND approved_by IS NULL AND revoked_at IS NULL
                """)
                .param("id", id)
                .param("approverId", approverId)
                .param("approvedAt", Timestamp.from(approvedAt))
                .update();
    }

    @Override
    public void revoke(UUID id, UUID revokedBy, Instant revokedAt) {
        jdbcClient.sql("""
                UPDATE temporary_access_grant SET revoked_at = :revokedAt, revoked_by = :revokedBy
                WHERE id = :id AND revoked_at IS NULL
                """)
                .param("id", id)
                .param("revokedBy", revokedBy)
                .param("revokedAt", Timestamp.from(revokedAt))
                .update();
    }

    @Override
    public List<TemporaryAccessGrant> current(UUID breweryId) {
        return jdbcClient.sql(SELECT + " WHERE t.brewery_id = :breweryId ORDER BY t.valid_from DESC")
                .param("breweryId", breweryId)
                .query(JdbcTemporaryAccessRepository::map)
                .list();
    }

    private static TemporaryAccessGrant map(ResultSet rs, int rowNum) throws SQLException {
        return new TemporaryAccessGrant(
                rs.getObject("id", UUID.class),
                rs.getObject("brewery_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("permission_id", UUID.class),
                rs.getString("code"),
                rs.getBoolean("critical"),
                rs.getString("reason"),
                instant(rs.getTimestamp("valid_from")),
                instant(rs.getTimestamp("valid_until")),
                rs.getObject("requested_by", UUID.class),
                rs.getObject("approved_by", UUID.class),
                instant(rs.getTimestamp("revoked_at")));
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
