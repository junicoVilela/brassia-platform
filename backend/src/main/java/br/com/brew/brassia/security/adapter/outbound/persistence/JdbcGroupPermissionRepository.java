package br.com.brew.brassia.security.adapter.outbound.persistence;

import br.com.brew.brassia.security.application.port.outbound.GroupPermissionRepository;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcGroupPermissionRepository implements GroupPermissionRepository {
    private final JdbcClient jdbc;

    JdbcGroupPermissionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<String> findPermissionCodesByGroupId(UUID groupId) {
        return new LinkedHashSet<>(jdbc.sql("""
                SELECT p.code FROM group_permission gp
                JOIN security_permission p ON p.id = gp.permission_id AND p.active
                WHERE gp.group_id = :groupId
                """)
                .param("groupId", groupId)
                .query(String.class).list());
    }
}
