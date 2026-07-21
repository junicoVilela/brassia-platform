package br.com.brew.brassia.security.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.security.application.port.inbound.AdministerAccountUseCase.Command;
import br.com.brew.brassia.security.application.port.inbound.AdministerAccountUseCase.Operation;
import br.com.brew.brassia.security.application.port.outbound.SecurityUserRepository;
import br.com.brew.brassia.security.application.port.outbound.UserSessionRegistry;
import br.com.brew.brassia.security.domain.AccountStatus;
import br.com.brew.brassia.security.domain.DisplayName;
import br.com.brew.brassia.security.domain.EmailAddress;
import br.com.brew.brassia.security.domain.SecurityUser;
import br.com.brew.brassia.security.domain.UserId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdministerAccountHandlerTest {

    private final List<AuditEvent> audited = new ArrayList<>();
    private final AuditTrail audit = audited::add;
    private final FakeUsers users = new FakeUsers();
    private final FakeSessions sessions = new FakeSessions();

    private AdministerAccountHandler handler() {
        return new AdministerAccountHandler(users, sessions, audit);
    }

    private SecurityUser storedActive() {
        var user = SecurityUser.reconstitute(UserId.newId(), new EmailAddress("brewer@example.com"),
                new DisplayName("Brewer"), AccountStatus.ACTIVE, Instant.now(), 1);
        users.store.put(user.id(), user);
        return user;
    }

    @Test
    void blockLocksAccountAndAudits() {
        var user = storedActive();

        var result = handler().handle(new Command(UUID.randomUUID(), UUID.randomUUID(), user.id().value(), Operation.BLOCK));

        assertThat(result.status()).isEqualTo(AccountStatus.LOCKED.name());
        assertThat(users.store.get(user.id()).status()).isEqualTo(AccountStatus.LOCKED);
        assertThat(audited).singleElement().satisfies(e -> assertThat(e.action()).isEqualTo("security.user.block"));
        assertThat(sessions.revoked).isEmpty();
    }

    @Test
    void unblockActivatesAccount() {
        var user = storedActive();
        user.block();

        var result = handler().handle(new Command(UUID.randomUUID(), UUID.randomUUID(), user.id().value(), Operation.UNBLOCK));

        assertThat(result.status()).isEqualTo(AccountStatus.ACTIVE.name());
        assertThat(audited).singleElement().satisfies(e -> assertThat(e.action()).isEqualTo("security.user.unblock"));
    }

    @Test
    void disableRevokesSessionsAndAudits() {
        var user = storedActive();

        handler().handle(new Command(UUID.randomUUID(), UUID.randomUUID(), user.id().value(), Operation.DISABLE));

        assertThat(users.store.get(user.id()).status()).isEqualTo(AccountStatus.DISABLED);
        assertThat(sessions.revoked).containsExactly(user.id());
        assertThat(audited).singleElement().satisfies(e -> assertThat(e.action()).isEqualTo("security.user.disable"));
    }

    @Test
    void rejectsUnknownAccount() {
        assertThatThrownBy(() -> handler()
                .handle(new Command(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Operation.BLOCK)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(audited).isEmpty();
    }

    @Test
    void rejectsInvalidTransition() {
        var user = storedActive(); // ACTIVE não pode ser desbloqueada

        assertThatThrownBy(() -> handler()
                .handle(new Command(UUID.randomUUID(), UUID.randomUUID(), user.id().value(), Operation.UNBLOCK)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(sessions.revoked).isEmpty();
    }

    private static final class FakeUsers implements SecurityUserRepository {
        final Map<UserId, SecurityUser> store = new HashMap<>();

        @Override public boolean existsByNormalizedEmail(String normalizedEmail) { return false; }
        @Override public Optional<SecurityUser> findById(UserId id) { return Optional.ofNullable(store.get(id)); }
        @Override public void save(SecurityUser user) { store.put(user.id(), user); }
    }

    private static final class FakeSessions implements UserSessionRegistry {
        final List<UserId> revoked = new ArrayList<>();

        @Override public void revokeAll(UserId userId) { revoked.add(userId); }
    }
}
