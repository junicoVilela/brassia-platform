package br.com.brew.brassia.security.adapter.outbound.persistence;

import br.com.brew.brassia.security.application.port.outbound.GroupMembershipRepository;
import br.com.brew.brassia.security.domain.UserId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcGroupMembershipRepository implements GroupMembershipRepository {
    private final JdbcClient jdbcClient;

    JdbcGroupMembershipRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<UUID> groupIdByCode(String code) {
        return jdbcClient.sql("SELECT id FROM security_group WHERE code = :code AND brewery_id IS NULL")
                .param("code", code)
                .query(UUID.class)
                .optional();
    }

    @Override
    public boolean hasMembership(UserId userId, UUID groupId) {
        return jdbcClient.sql("""
                SELECT count(*) FROM user_group_membership
                WHERE user_id = :userId AND group_id = :groupId AND revoked_at IS NULL
                """)
                .param("userId", userId.value())
                .param("groupId", groupId)
                .query(Long.class)
                .single() > 0;
    }

    @Override
    public void addMembership(UserId userId, UUID groupId) {
        jdbcClient.sql("""
                INSERT INTO user_group_membership (id, user_id, group_id)
                VALUES (:id, :userId, :groupId)
                ON CONFLICT (user_id, group_id) DO NOTHING
                """)
                .param("id", UUID.randomUUID())
                .param("userId", userId.value())
                .param("groupId", groupId)
                .update();
    }
}
