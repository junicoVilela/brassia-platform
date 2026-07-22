package br.com.brew.brassia.security.adapter.outbound.persistence;

import br.com.brew.brassia.security.application.port.outbound.ApiCredentialRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcApiCredentialRepository implements ApiCredentialRepository {
    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    JdbcApiCredentialRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UUID issue(UUID serviceAccountId, String keyPrefix, String keyHash, List<String> scopes, Instant expiresAt) {
        var id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO api_credential (id, service_account_id, key_prefix, key_hash, scopes, expires_at)
                VALUES (:id, :saId, :prefix, :hash, :scopes::jsonb, :expires)
                """)
                .param("id", id)
                .param("saId", serviceAccountId)
                .param("prefix", keyPrefix)
                .param("hash", keyHash)
                .param("scopes", toJson(scopes))
                .param("expires", expiresAt == null ? null : Timestamp.from(expiresAt))
                .update();
        return id;
    }

    @Override
    public void revoke(UUID credentialId) {
        jdbc.sql("UPDATE api_credential SET revoked_at = now() WHERE id = :id AND revoked_at IS NULL")
                .param("id", credentialId)
                .update();
    }

    @Override
    public List<CredentialView> listByServiceAccount(UUID serviceAccountId) {
        return jdbc.sql("""
                SELECT id, key_prefix, scopes, expires_at, revoked_at
                FROM api_credential WHERE service_account_id = :saId ORDER BY created_at DESC
                """)
                .param("saId", serviceAccountId)
                .query((rs, n) -> new CredentialView(
                        rs.getObject("id", UUID.class),
                        rs.getString("key_prefix"),
                        parseScopes(rs.getString("scopes")),
                        rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toInstant(),
                        rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant()))
                .list();
    }

    @Override
    public Optional<ActiveCredential> findActiveByPrefix(String prefix) {
        return jdbc.sql("""
                SELECT c.id, c.service_account_id, c.key_hash, c.scopes, s.brewery_id
                FROM api_credential c
                JOIN service_account s ON s.id = c.service_account_id AND s.active
                WHERE c.key_prefix = :prefix AND c.revoked_at IS NULL
                  AND (c.expires_at IS NULL OR c.expires_at > now())
                """)
                .param("prefix", prefix)
                .query((rs, n) -> new ActiveCredential(
                        rs.getObject("id", UUID.class),
                        rs.getObject("service_account_id", UUID.class),
                        rs.getObject("brewery_id", UUID.class),
                        rs.getString("key_hash"),
                        parseScopes(rs.getString("scopes"))))
                .optional();
    }

    @Override
    public void touchLastUsed(UUID credentialId, Instant now) {
        jdbc.sql("UPDATE api_credential SET last_used_at = :now WHERE id = :id")
                .param("now", Timestamp.from(now))
                .param("id", credentialId)
                .update();
    }

    private String toJson(List<String> scopes) {
        try {
            return objectMapper.writeValueAsString(scopes);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private List<String> parseScopes(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
