package br.com.brew.brassia.security.adapter.outbound.persistence;

import br.com.brew.brassia.security.application.port.outbound.FederationProviderRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcFederationProviderRepository implements FederationProviderRepository {
    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    JdbcFederationProviderRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UUID create(UUID breweryId, String code, String displayName, String protocol,
            String issuerOrEntityId, Map<String, Object> configuration) {
        var id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO federation_provider (id, brewery_id, code, display_name, protocol, status,
                    issuer_or_entity_id, configuration)
                VALUES (:id, :breweryId, :code, :name, :protocol, 'DRAFT', :issuer, :config::jsonb)
                """)
                .param("id", id)
                .param("breweryId", breweryId)
                .param("code", code)
                .param("name", displayName)
                .param("protocol", protocol)
                .param("issuer", issuerOrEntityId)
                .param("config", toJson(configuration))
                .update();
        return id;
    }

    @Override
    public Optional<ProviderView> findById(UUID id) {
        return jdbc.sql("""
                SELECT id, brewery_id, code, display_name, protocol, status, issuer_or_entity_id,
                       metadata_uri, configuration, jit_mode, version
                FROM federation_provider WHERE id = :id
                """)
                .param("id", id)
                .query(this::map)
                .optional();
    }

    @Override
    public List<ProviderView> listByBrewery(UUID breweryId) {
        return jdbc.sql("""
                SELECT id, brewery_id, code, display_name, protocol, status, issuer_or_entity_id,
                       metadata_uri, configuration, jit_mode, version
                FROM federation_provider WHERE brewery_id = :breweryId ORDER BY created_at
                """)
                .param("breweryId", breweryId)
                .query(this::map)
                .list();
    }

    @Override
    public void update(UUID id, String displayName, String status, Map<String, Object> configuration, long version) {
        jdbc.sql("""
                UPDATE federation_provider SET display_name = :name, status = :status,
                    configuration = :config::jsonb, version = version + 1
                WHERE id = :id AND version = :version
                """)
                .param("name", displayName)
                .param("status", status)
                .param("config", toJson(configuration))
                .param("id", id)
                .param("version", version)
                .update();
    }

    private ProviderView map(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new ProviderView(
                rs.getObject("id", UUID.class),
                rs.getObject("brewery_id", UUID.class),
                rs.getString("code"),
                rs.getString("display_name"),
                rs.getString("protocol"),
                rs.getString("status"),
                rs.getString("issuer_or_entity_id"),
                rs.getString("metadata_uri"),
                parseConfig(rs.getString("configuration")),
                rs.getBoolean("jit_mode"),
                rs.getLong("version"));
    }

    private String toJson(Map<String, Object> config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> parseConfig(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }
}
