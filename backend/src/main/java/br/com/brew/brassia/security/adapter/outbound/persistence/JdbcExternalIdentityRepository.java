package br.com.brew.brassia.security.adapter.outbound.persistence;

import br.com.brew.brassia.security.application.port.outbound.ExternalIdentityRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcExternalIdentityRepository implements ExternalIdentityRepository {
    private final JdbcClient jdbc;

    JdbcExternalIdentityRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void link(UUID providerId, UUID userId, String externalSubject, String normalizedEmail) {
        jdbc.sql("""
                INSERT INTO external_identity (id, provider_id, user_id, external_subject, normalized_email_at_link)
                VALUES (:id, :providerId, :userId, :subject, :email)
                ON CONFLICT (provider_id, external_subject) DO UPDATE SET user_id = EXCLUDED.user_id
                """)
                .param("id", UUID.randomUUID())
                .param("providerId", providerId)
                .param("userId", userId)
                .param("subject", externalSubject)
                .param("email", normalizedEmail)
                .update();
    }

    @Override
    public Optional<UUID> resolveUserId(UUID providerId, String externalSubject) {
        return jdbc.sql("""
                SELECT user_id FROM external_identity
                WHERE provider_id = :providerId AND external_subject = :subject
                """)
                .param("providerId", providerId)
                .param("subject", externalSubject)
                .query(UUID.class)
                .optional();
    }
}
