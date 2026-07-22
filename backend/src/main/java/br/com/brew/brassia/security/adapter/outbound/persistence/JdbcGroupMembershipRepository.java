package br.com.brew.brassia.security.adapter.outbound.persistence;

import br.com.brew.brassia.security.application.port.outbound.GroupMembershipRepository;
import br.com.brew.brassia.security.domain.UserId;
import java.util.List;
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
    public boolean groupActiveById(UUID groupId) {
        return jdbcClient.sql("SELECT count(*) FROM security_group WHERE id = :groupId AND active")
                .param("groupId", groupId)
                .query(Long.class).single() > 0;
    }

    @Override
    public boolean hasActiveMembership(UserId userId, UUID groupId, UUID breweryId) {
        return jdbcClient.sql("""
                SELECT count(*) FROM user_group_membership
                WHERE user_id = :userId AND group_id = :groupId AND revoked_at IS NULL
                  AND brewery_id IS NOT DISTINCT FROM :breweryId
                """)
                .param("userId", userId.value())
                .param("groupId", groupId)
                .param("breweryId", breweryId)
                .query(Long.class).single() > 0;
    }

    @Override
    public void addMembership(UserId userId, UUID groupId, UUID breweryId) {
        jdbcClient.sql("""
                INSERT INTO user_group_membership (id, brewery_id, user_id, group_id)
                VALUES (:id, :breweryId, :userId, :groupId)
                ON CONFLICT (user_id, group_id, brewery_id) DO NOTHING
                """)
                .param("id", UUID.randomUUID())
                .param("breweryId", breweryId)
                .param("userId", userId.value())
                .param("groupId", groupId)
                .update();
    }

    @Override
    public void revokeMembership(UserId userId, UUID groupId, UUID breweryId) {
        jdbcClient.sql("""
                UPDATE user_group_membership SET revoked_at = now()
                WHERE user_id = :userId AND group_id = :groupId AND revoked_at IS NULL
                  AND brewery_id IS NOT DISTINCT FROM :breweryId
                """)
                .param("userId", userId.value())
                .param("groupId", groupId)
                .param("breweryId", breweryId)
                .update();
    }

    @Override
    public List<MembershipRecord> listActiveByBrewery(UUID breweryId) {
        return jdbcClient.sql("""
                SELECT user_id, group_id FROM user_group_membership
                WHERE revoked_at IS NULL AND brewery_id IS NOT DISTINCT FROM :breweryId
                """)
                .param("breweryId", breweryId)
                .query((rs, n) -> new MembershipRecord(
                        rs.getObject("user_id", UUID.class),
                        rs.getObject("group_id", UUID.class)))
                .list();
    }
}
