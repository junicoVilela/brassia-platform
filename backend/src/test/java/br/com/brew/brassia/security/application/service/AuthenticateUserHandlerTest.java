package br.com.brew.brassia.security.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditOutcome;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.security.application.port.inbound.AuthenticateUserUseCase.Command;
import br.com.brew.brassia.security.application.port.outbound.PasswordCredentialRepository;
import br.com.brew.brassia.security.application.port.outbound.PasswordHasher;
import br.com.brew.brassia.security.application.port.outbound.SecurityUserRepository;
import br.com.brew.brassia.security.domain.AccountStatus;
import br.com.brew.brassia.security.domain.DisplayName;
import br.com.brew.brassia.security.domain.EmailAddress;
import br.com.brew.brassia.security.domain.PasswordCredential;
import br.com.brew.brassia.security.domain.SecurityUser;
import br.com.brew.brassia.security.domain.UserId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AuthenticateUserHandlerTest {

    private final List<AuditEvent> audited = new ArrayList<>();
    private final AuditTrail audit = audited::add;
    private final FakeUsers users = new FakeUsers();
    private final FakeCredentials credentials = new FakeCredentials();
    private final PasswordHasher passwordHasher = new PasswordHasher() {
        @Override public String hash(CharSequence raw) { return "enc:" + raw; }
        @Override public boolean matches(CharSequence raw, String encoded) { return encoded.equals("enc:" + raw); }
        @Override public boolean needsUpgrade(String encoded) { return false; }
    };

    private AuthenticateUserHandler handler() {
        return new AuthenticateUserHandler(users, credentials, passwordHasher, userId -> false, audit);
    }

    private SecurityUser storedActive(String email) {
        var user = SecurityUser.reconstitute(UserId.newId(), new EmailAddress(email),
                new DisplayName("Brewer"), AccountStatus.ACTIVE, Instant.now(), 1);
        users.store.put(user.email().normalized(), user);
        credentials.store.put(user.id(), new PasswordCredential(user.id(), "enc:segredo1", "delegating"));
        return user;
    }

    @Test
    void authenticatesValidCredentials() {
        var user = storedActive("brewer@example.com");

        var result = handler().handle(new Command("Brewer@Example.com", "segredo1"));

        assertThat(result.userId()).isEqualTo(user.id().value());
        assertThat(result.email()).isEqualTo("brewer@example.com");
        assertThat(audited).singleElement().satisfies(e -> {
            assertThat(e.action()).isEqualTo("security.login.success");
            assertThat(e.outcome()).isEqualTo(AuditOutcome.SUCCESS);
        });
    }

    @Test
    void rejectsWrongPasswordGenerically() {
        storedActive("brewer@example.com");

        assertThatThrownBy(() -> handler().handle(new Command("brewer@example.com", "errada")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(audited).singleElement().satisfies(e -> assertThat(e.outcome()).isEqualTo(AuditOutcome.FAILURE));
    }

    @Test
    void rejectsUnknownEmailGenerically() {
        assertThatThrownBy(() -> handler().handle(new Command("ghost@example.com", "segredo1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(audited).singleElement().satisfies(e -> assertThat(e.action()).isEqualTo("security.login.failure"));
    }

    @Test
    void rejectsNonActiveAccount() {
        var user = SecurityUser.invite(new EmailAddress("invited@example.com"), new DisplayName("Invited"));
        users.store.put(user.email().normalized(), user);
        credentials.store.put(user.id(), new PasswordCredential(user.id(), "enc:segredo1", "delegating"));

        assertThatThrownBy(() -> handler().handle(new Command("invited@example.com", "segredo1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static final class FakeUsers implements SecurityUserRepository {
        final Map<String, SecurityUser> store = new HashMap<>();

        @Override public boolean existsByNormalizedEmail(String normalizedEmail) { return store.containsKey(normalizedEmail); }
        @Override public Optional<SecurityUser> findByNormalizedEmail(String normalizedEmail) { return Optional.ofNullable(store.get(normalizedEmail)); }
        @Override public Optional<SecurityUser> findById(UserId id) { return Optional.empty(); }
        @Override public List<SecurityUser> findPage(int page, int size) { return List.copyOf(store.values()); }
        @Override public long count() { return store.size(); }
        @Override public void save(SecurityUser user) { store.put(user.email().normalized(), user); }
    }

    private static final class FakeCredentials implements PasswordCredentialRepository {
        final Map<UserId, PasswordCredential> store = new HashMap<>();

        @Override public void save(PasswordCredential credential) { store.put(credential.userId(), credential); }
        @Override public Optional<PasswordCredential> findByUserId(UserId userId) { return Optional.ofNullable(store.get(userId)); }
    }
}
