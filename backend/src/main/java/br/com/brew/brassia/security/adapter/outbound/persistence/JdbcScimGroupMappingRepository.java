package br.com.brew.brassia.security.adapter.outbound.persistence;

import br.com.brew.brassia.security.application.port.outbound.ScimGroupMappingRepository;
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
}
