package br.com.brew.brassia.security.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditOutcome;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.security.application.port.inbound.InviteUserUseCase.Command;
import br.com.brew.brassia.security.application.port.outbound.AccountTokenRepository;
import br.com.brew.brassia.security.application.port.outbound.NotificationGateway;
import br.com.brew.brassia.security.application.port.outbound.SecurityUserRepository;
import br.com.brew.brassia.security.application.port.outbound.TokenHasher;
import br.com.brew.brassia.security.domain.AccountStatus;
import br.com.brew.brassia.security.domain.AccountToken;
import br.com.brew.brassia.security.domain.EmailAddress;
import br.com.brew.brassia.security.domain.SecurityUser;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InviteUserHandlerTest {

    private final List<AuditEvent> audited = new ArrayList<>();
    private final AuditTrail audit = audited::add;
    private final InMemorySecurityUserRepository users = new InMemorySecurityUserRepository();
    private final InMemoryAccountTokenRepository tokens = new InMemoryAccountTokenRepository();
    private final CapturingNotificationGateway notifications = new CapturingNotificationGateway();
    private final TokenHasher tokenHasher = raw -> "hash:" + raw;

    private InviteUserHandler handler() {
        return new InviteUserHandler(users, tokens, tokenHasher, notifications, audit);
    }

    @Test
    void invitesUserPersistsTokenNotifiesAndAudits() {
        var actorId = UUID.randomUUID();
        var breweryId = UUID.randomUUID();

        var result = handler().handle(new Command(actorId, breweryId, "Brewer@Example.com", "Brewer"));

        assertThat(result.status()).isEqualTo(AccountStatus.INVITED.name());
        assertThat(result.email()).isEqualTo("Brewer@Example.com");

        assertThat(users.saved).hasSize(1);
        assertThat(users.saved.get(0).status()).isEqualTo(AccountStatus.INVITED);
        assertThat(tokens.saved).hasSize(1);
        assertThat(tokens.saved.get(0).type()).isEqualTo(AccountToken.Type.INVITATION);

        // Token bruto entregue pela notificação, hash persistido — nunca o inverso.
        assertThat(notifications.rawToken).isNotBlank();
        assertThat(tokens.saved.get(0).tokenHash()).isEqualTo("hash:" + notifications.rawToken);

        assertThat(audited).hasSize(1);
        var event = audited.get(0);
        assertThat(event.action()).isEqualTo("security.user.invite");
        assertThat(event.resourceType()).isEqualTo("security_user");
        assertThat(event.breweryId()).isEqualTo(breweryId);
        assertThat(event.actorId()).isEqualTo(actorId);
        assertThat(event.outcome()).isEqualTo(AuditOutcome.SUCCESS);
        // Auditoria não carrega o token bruto.
        assertThat(event.metadata().values()).doesNotContain(notifications.rawToken);
    }

    @Test
    void rejectsDuplicateEmailWithoutPersisting() {
        users.existing.add("brewer@example.com");

        assertThatThrownBy(() -> handler()
                .handle(new Command(UUID.randomUUID(), UUID.randomUUID(), "brewer@example.com", "Brewer")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(users.saved).isEmpty();
        assertThat(tokens.saved).isEmpty();
        assertThat(audited).isEmpty();
        assertThat(notifications.rawToken).isNull();
    }

    private static final class InMemorySecurityUserRepository implements SecurityUserRepository {
        final List<SecurityUser> saved = new ArrayList<>();
        final List<String> existing = new ArrayList<>();

        @Override
        public boolean existsByNormalizedEmail(String normalizedEmail) {
            return existing.contains(normalizedEmail);
        }

        @Override
        public void save(SecurityUser user) {
            saved.add(user);
        }
    }

    private static final class InMemoryAccountTokenRepository implements AccountTokenRepository {
        final List<AccountToken> saved = new ArrayList<>();

        @Override
        public void save(AccountToken token) {
            saved.add(token);
        }
    }

    private static final class CapturingNotificationGateway implements NotificationGateway {
        String rawToken;

        @Override
        public void sendInvitation(EmailAddress email, String rawToken, Instant expiresAt) {
            this.rawToken = rawToken;
        }
    }
}
