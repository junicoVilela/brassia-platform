package br.com.brew.brassia.security.adapter.outbound.persistence;

import br.com.brew.brassia.security.application.port.outbound.BreweryAccessRepository;
import br.com.brew.brassia.security.domain.UserId;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcBreweryAccessRepository implements BreweryAccessRepository {
    private static final String ACTIVE = """
            m.revoked_at IS NULL AND m.valid_from <= now()
            AND (m.valid_until IS NULL OR m.valid_until > now())
            """;

    private final JdbcClient jdbcClient;

    JdbcBreweryAccessRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public boolean hasGlobalMembership(UserId userId) {
        return jdbcClient.sql("""
                SELECT count(*) FROM user_group_membership m
                WHERE m.user_id = :userId AND m.brewery_id IS NULL AND %s
                """.formatted(ACTIVE))
                .param("userId", userId.value())
                .query(Long.class).single() > 0;
    }

    @Override
    public List<UUID> scopedBreweryIds(UserId userId) {
        return jdbcClient.sql("""
                SELECT DISTINCT m.brewery_id FROM user_group_membership m
                WHERE m.user_id = :userId AND m.brewery_id IS NOT NULL AND %s
                """.formatted(ACTIVE))
                .param("userId", userId.value())
                .query(UUID.class).list();
    }
}
