package br.com.brew.brassia.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.brew.brassia.security.application.port.outbound.EffectivePermissionsRepository;
import br.com.brew.brassia.security.domain.UserId;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** SEC-008: concessões temporárias entram na resolução de permissões do login. */
@SpringBootTest
@Testcontainers
class TemporaryAccessResolutionIT {

    private static final String COMMON = "security.user.read";       // critical=false
    private static final String CRITICAL = "security.membership.manage"; // critical=true

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired EffectivePermissionsRepository permissions;
    @Autowired JdbcClient jdbc;

    private final UUID brewery = UUID.randomUUID();

    @Test
    void commonGrantIsEffectiveAndExpiredIsNot() {
        var user = user();
        grant(user, COMMON, hoursFromNow(-1), hoursFromNow(1), null, null); // ativa
        grant(user, CRITICAL, hoursFromNow(-2), hoursFromNow(-1), user(), null); // expirada (aprovada)

        var resolved = permissions.findByUserId(new UserId(user), brewery);

        assertThat(resolved).contains(COMMON).doesNotContain(CRITICAL);
    }

    @Test
    void criticalGrantRequiresApproval() {
        var user = user();
        var id = grant(user, CRITICAL, hoursFromNow(-1), hoursFromNow(1), null, null); // pendente

        assertThat(permissions.findByUserId(new UserId(user), brewery)).doesNotContain(CRITICAL);

        jdbc.sql("UPDATE temporary_access_grant SET approved_by = :approver WHERE id = :id")
                .param("approver", user()).param("id", id).update();

        assertThat(permissions.findByUserId(new UserId(user), brewery)).contains(CRITICAL);
    }

    @Test
    void revokedAndOtherBreweryAreExcluded() {
        var user = user();
        grant(user, COMMON, hoursFromNow(-1), hoursFromNow(1), null, hoursFromNow(0)); // revogada
        var otherBrewery = UUID.randomUUID();
        grantOn(otherBrewery, user, COMMON, hoursFromNow(-1), hoursFromNow(1), null, null);

        assertThat(permissions.findByUserId(new UserId(user), brewery)).doesNotContain(COMMON);
    }

    private UUID user() {
        var id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO security_user (id, email, normalized_email, display_name, status)
                VALUES (:id, :email, :email, 'T', 'ACTIVE')
                """)
                .param("id", id)
                .param("email", id + "@x.com")
                .update();
        return id;
    }

    private UUID grant(UUID user, String permissionCode, Instant from, Instant until, UUID approvedBy, Instant revokedAt) {
        return grantOn(brewery, user, permissionCode, from, until, approvedBy, revokedAt);
    }

    private UUID grantOn(UUID breweryId, UUID user, String permissionCode, Instant from, Instant until,
            UUID approvedBy, Instant revokedAt) {
        var id = UUID.randomUUID();
        var permissionId = jdbc.sql("SELECT id FROM security_permission WHERE code = :code")
                .param("code", permissionCode).query(UUID.class).single();
        jdbc.sql("""
                INSERT INTO temporary_access_grant
                    (id, brewery_id, user_id, permission_id, reason, valid_from, valid_until,
                     requested_by, approved_by, revoked_at)
                VALUES (:id, :brewery, :user, :permission, 'motivo', :from, :until, :requester, :approvedBy, :revokedAt)
                """)
                .param("id", id)
                .param("brewery", breweryId)
                .param("user", user)
                .param("permission", permissionId)
                .param("from", Timestamp.from(from))
                .param("until", Timestamp.from(until))
                .param("requester", user)
                .param("approvedBy", approvedBy)
                .param("revokedAt", revokedAt == null ? null : Timestamp.from(revokedAt))
                .update();
        return id;
    }

    private static Instant hoursFromNow(int hours) {
        return Instant.now().plus(hours, ChronoUnit.HOURS);
    }
}
