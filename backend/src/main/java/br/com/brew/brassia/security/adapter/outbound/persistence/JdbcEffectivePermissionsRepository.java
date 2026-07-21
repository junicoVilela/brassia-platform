package br.com.brew.brassia.security.adapter.outbound.persistence;

import br.com.brew.brassia.security.application.port.outbound.EffectivePermissionsRepository;
import br.com.brew.brassia.security.domain.UserId;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Resolve permissões efetivas juntando associações ativas → grupos ativos →
 * permissões ativas. Consulta de leitura (sem entidade JPA), via JdbcClient.
 */
@Repository
class JdbcEffectivePermissionsRepository implements EffectivePermissionsRepository {
    private static final String SQL = """
            SELECT DISTINCT p.code
            FROM user_group_membership m
            JOIN security_group g ON g.id = m.group_id AND g.active
            JOIN group_permission gp ON gp.group_id = g.id
            JOIN security_permission p ON p.id = gp.permission_id AND p.active
            WHERE m.user_id = :userId
              AND m.revoked_at IS NULL
              AND m.valid_from <= now()
              AND (m.valid_until IS NULL OR m.valid_until > now())
            """;

    private final JdbcClient jdbcClient;

    JdbcEffectivePermissionsRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Set<String> findByUserId(UserId userId) {
        return new LinkedHashSet<>(
                jdbcClient.sql(SQL).param("userId", userId.value()).query(String.class).list());
    }
}
