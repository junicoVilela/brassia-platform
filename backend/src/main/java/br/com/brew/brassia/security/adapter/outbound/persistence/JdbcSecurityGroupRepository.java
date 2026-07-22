package br.com.brew.brassia.security.adapter.outbound.persistence;

import br.com.brew.brassia.security.application.port.outbound.SecurityGroupRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSecurityGroupRepository implements SecurityGroupRepository {
    private final JdbcClient jdbcClient;

    JdbcSecurityGroupRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public boolean existsByCode(UUID breweryId, String code) {
        Integer count = jdbcClient.sql("""
                SELECT COUNT(*) FROM security_group
                WHERE code = :code AND brewery_id IS NOT DISTINCT FROM :breweryId
                """)
                .param("code", code)
                .param("breweryId", breweryId)
                .query(Integer.class)
                .single();
        return count > 0;
    }

    @Override
    public Optional<GroupRecord> findById(UUID id) {
        return jdbcClient.sql("""
                SELECT id, brewery_id, code, name, description, system_group, active, version
                FROM security_group WHERE id = :id
                """)
                .param("id", id)
                .query((rs, n) -> new GroupRecord(
                        rs.getObject("id", UUID.class),
                        rs.getObject("brewery_id", UUID.class),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getBoolean("system_group"),
                        rs.getBoolean("active"),
                        rs.getLong("version")))
                .optional();
    }

    @Override
    public UUID insert(NewGroup group) {
        var id = UUID.randomUUID();
        jdbcClient.sql("""
                INSERT INTO security_group (id, brewery_id, code, name, description, system_group, active, created_at, version)
                VALUES (:id, :breweryId, :code, :name, :description, false, true, :createdAt, 0)
                """)
                .param("id", id)
                .param("breweryId", group.breweryId())
                .param("code", group.code())
                .param("name", group.name())
                .param("description", group.description())
                .param("createdAt", Timestamp.from(Instant.now()))
                .update();
        return id;
    }

    @Override
    public boolean update(UUID id, String name, String description, long expectedVersion) {
        int updated = jdbcClient.sql("""
                UPDATE security_group
                SET name = :name, description = :description, version = version + 1
                WHERE id = :id AND version = :version AND system_group = false AND active = true
                """)
                .param("id", id)
                .param("name", name)
                .param("description", description)
                .param("version", expectedVersion)
                .update();
        return updated == 1;
    }

    @Override
    public void replacePermissions(UUID groupId, List<UUID> permissionIds) {
        jdbcClient.sql("DELETE FROM group_permission WHERE group_id = :groupId")
                .param("groupId", groupId)
                .update();
        for (UUID permissionId : permissionIds) {
            jdbcClient.sql("""
                    INSERT INTO group_permission (group_id, permission_id, granted_at)
                    VALUES (:groupId, :permissionId, :grantedAt)
                    """)
                    .param("groupId", groupId)
                    .param("permissionId", permissionId)
                    .param("grantedAt", Timestamp.from(Instant.now()))
                    .update();
        }
    }

    @Override
    public List<UUID> resolveActivePermissionIds(List<String> codes) {
        if (codes.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = new ArrayList<>();
        for (String code : codes) {
            Optional<UUID> id = jdbcClient.sql("""
                    SELECT id FROM security_permission WHERE code = :code AND active = true
                    """)
                    .param("code", code)
                    .query(UUID.class)
                    .optional();
            if (id.isEmpty()) {
                throw new IllegalArgumentException("permissão inexistente ou inativa: " + code);
            }
            ids.add(id.get());
        }
        return List.copyOf(ids);
    }

    @Override
    public List<String> permissionCodesOf(UUID groupId) {
        return jdbcClient.sql("""
                SELECT p.code
                FROM group_permission gp
                JOIN security_permission p ON p.id = gp.permission_id AND p.active
                WHERE gp.group_id = :groupId
                ORDER BY p.code
                """)
                .param("groupId", groupId)
                .query(String.class)
                .list();
    }
}
