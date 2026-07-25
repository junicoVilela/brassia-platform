package br.com.brew.brassia.security.adapter.outbound.persistence;

import br.com.brew.brassia.security.application.port.outbound.ScimGroupMappingRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcScimGroupMappingRepository implements ScimGroupMappingRepository {
    private final JdbcClient jdbc;

    JdbcScimGroupMappingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Mapping> findActive(UUID providerId, String externalGroupId) {
        return jdbc.sql("""
                SELECT security_group_id, active FROM scim_group_mapping
                WHERE provider_id = :providerId AND external_group_id = :externalGroupId AND active
                """)
                .param("providerId", providerId)
                .param("externalGroupId", externalGroupId)
                .query((rs, n) -> new Mapping(rs.getObject("security_group_id", UUID.class), rs.getBoolean("active")))
                .optional();
    }

    @Override
    public void create(UUID providerId, String externalGroupId, UUID securityGroupId) {
        jdbc.sql("""
                INSERT INTO scim_group_mapping (id, provider_id, external_group_id, security_group_id)
                VALUES (:id, :providerId, :externalGroupId, :groupId)
                ON CONFLICT (provider_id, external_group_id) DO NOTHING
                """)
                .param("id", UUID.randomUUID())
                .param("providerId", providerId)
                .param("externalGroupId", externalGroupId)
                .param("groupId", securityGroupId)
                .update();
    }

    @Override
    public List<MappingView> listByProvider(UUID providerId) {
        return jdbc.sql("""
                SELECT external_group_id, security_group_id, active FROM scim_group_mapping
                WHERE provider_id = :providerId
                ORDER BY external_group_id
                """)
                .param("providerId", providerId)
                .query((rs, n) -> new MappingView(
                        rs.getString("external_group_id"),
                        rs.getObject("security_group_id", UUID.class),
                        rs.getBoolean("active")))
                .list();
    }

    @Override
    public void upsert(UUID providerId, String externalGroupId, UUID securityGroupId) {
        jdbc.sql("""
                INSERT INTO scim_group_mapping (id, provider_id, external_group_id, security_group_id)
                VALUES (:id, :providerId, :externalGroupId, :groupId)
                ON CONFLICT (provider_id, external_group_id)
                DO UPDATE SET security_group_id = EXCLUDED.security_group_id, active = true
                """)
                .param("id", UUID.randomUUID())
                .param("providerId", providerId)
                .param("externalGroupId", externalGroupId)
                .param("groupId", securityGroupId)
                .update();
    }

    @Override
    public void deactivate(UUID providerId, String externalGroupId) {
        jdbc.sql("""
                UPDATE scim_group_mapping SET active = false
                WHERE provider_id = :providerId AND external_group_id = :externalGroupId
                """)
                .param("providerId", providerId)
                .param("externalGroupId", externalGroupId)
                .update();
    }
}
