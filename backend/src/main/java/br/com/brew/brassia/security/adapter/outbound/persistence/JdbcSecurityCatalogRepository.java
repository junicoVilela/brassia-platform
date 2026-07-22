package br.com.brew.brassia.security.adapter.outbound.persistence;

import br.com.brew.brassia.security.application.port.inbound.AccessCatalogQuery.GroupView;
import br.com.brew.brassia.security.application.port.inbound.AccessCatalogQuery.PermissionView;
import br.com.brew.brassia.security.application.port.outbound.SecurityCatalogRepository;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSecurityCatalogRepository implements SecurityCatalogRepository {
    private final JdbcClient jdbcClient;

    JdbcSecurityCatalogRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public List<PermissionView> listPermissions() {
        return jdbcClient.sql("""
                SELECT d.code AS domain, p.code, p.name, p.critical, p.active
                FROM security_permission p
                JOIN permission_domain d ON d.id = p.domain_id
                ORDER BY d.sort_order, p.code
                """)
                .query((rs, n) -> new PermissionView(
                        rs.getString("domain"), rs.getString("code"), rs.getString("name"),
                        rs.getBoolean("critical"), rs.getBoolean("active")))
                .list();
    }

    @Override
    public List<GroupView> listGroups() {
        return jdbcClient.sql("""
                SELECT g.id, g.code, g.name, g.description, g.brewery_id, g.system_group, g.active, g.version,
                       COALESCE(array_agg(p.code ORDER BY p.code) FILTER (WHERE p.code IS NOT NULL), '{}') AS permissions
                FROM security_group g
                LEFT JOIN group_permission gp ON gp.group_id = g.id
                LEFT JOIN security_permission p ON p.id = gp.permission_id AND p.active
                GROUP BY g.id, g.code, g.name, g.description, g.brewery_id, g.system_group, g.active, g.version
                ORDER BY g.code
                """)
                .query((rs, n) -> new GroupView(
                        rs.getObject("id", java.util.UUID.class),
                        rs.getString("code"), rs.getString("name"), rs.getString("description"),
                        rs.getObject("brewery_id", java.util.UUID.class),
                        rs.getBoolean("system_group"), rs.getBoolean("active"), rs.getLong("version"),
                        List.of((String[]) rs.getArray("permissions").getArray())))
                .list();
    }
}
