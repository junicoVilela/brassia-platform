package br.com.brew.brassia.security.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.security.application.port.inbound.AcceptInvitationUseCase.Command;
import br.com.brew.brassia.security.application.port.outbound.AccountTokenRepository;
import br.com.brew.brassia.security.application.port.outbound.PasswordCredentialRepository;
import br.com.brew.brassia.security.application.port.outbound.PasswordHasher;
import br.com.brew.brassia.security.application.port.outbound.SecurityUserRepository;
import br.com.brew.brassia.security.application.port.outbound.TokenHasher;
import br.com.brew.brassia.security.domain.AccountStatus;
import br.com.brew.brassia.security.domain.AccountToken;
import br.com.brew.brassia.security.domain.DisplayName;
import br.com.brew.brassia.security.domain.EmailAddress;
import br.com.brew.brassia.security.domain.PasswordCredential;
import br.com.brew.brassia.security.domain.SecurityUser;
import br.com.brew.brassia.security.domain.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AcceptInvitationHandlerTest {

    private final List<AuditEvent> audited = new ArrayList<>();
    private final AuditTrail audit = audited::add;
    private final FakeUsers users = new FakeUsers();
    private final FakeTokens tokens = new FakeTokens();
    private final FakeCredentials credentials = new FakeCredentials();
    private final TokenHasher tokenHasher = raw -> "hash:" + raw;
    private final PasswordHasher passwordHasher = new PasswordHasher() {
        @Override public String hash(CharSequence rawPassword) { return "enc:" + rawPassword; }
        @Override public boolean matches(CharSequence rawPassword, String encoded) { return encoded.equals("enc:" + rawPassword); }
        @Override public boolean needsUpgrade(String encoded) { return false; }
    };

    private AcceptInvitationHandler handler() {
        return new AcceptInvitationHandler(users, tokens, credentials, tokenHasher, passwordHasher, audit);
    }

    private SecurityUser invited() {
        var user = SecurityUser.invite(new EmailAddress("brewer@example.com"), new DisplayName("Brewer"));
        users.store.put(user.id(), user);
        return user;
    }

    @Test
    void activatesAccountAndConsumesToken() {
        var user = invited();
        tokens.store.put("hash:raw", AccountToken.invitation(user.id(), "hash:raw", Instant.now().plus(Duration.ofHours(1))));

        var result = handler().handle(new Command("raw", "segredo1"));

        assertThat(result.status()).isEqualTo(AccountStatus.ACTIVE.name());
        assertThat(users.store.get(user.id()).status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(tokens.store.get("hash:raw").usedAt()).isNotNull();
        // Credencial gravada com o hash da senha (nunca a senha em claro).
        assertThat(credentials.store.get(user.id()).passwordHash()).isEqualTo("enc:segredo1");
        assertThat(audited).singleElement().satisfies(e -> {
            assertThat(e.action()).isEqualTo("security.user.activate");
            assertThat(e.resourceType()).isEqualTo("security_user");
        });
    }

    @Test
    void rejectsUnknownToken() {
        assertThatThrownBy(() -> handler().handle(new Command("nope", "segredo1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(audited).isEmpty();
    }

    @Test
    void rejectsExpiredToken() {
        var user = invited();
        tokens.store.put("hash:raw", AccountToken.invitation(user.id(), "hash:raw", Instant.now()));

        assertThatThrownBy(() -> handler().handle(new Command("raw", "segredo1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(users.store.get(user.id()).status()).isEqualTo(AccountStatus.INVITED);
    }

    @Test
    void rejectsAlreadyUsedToken() {
        var user = invited();
        var token = AccountToken.invitation(user.id(), "hash:raw", Instant.now().plus(Duration.ofHours(1)));
        token.consume(Instant.now());
        tokens.store.put("hash:raw", token);

        assertThatThrownBy(() -> handler().handle(new Command("raw", "segredo1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsWhenAccountNotInvited() {
        var user = SecurityUser.reconstitute(UserId.newId(), new EmailAddress("brewer@example.com"),
                new DisplayName("Brewer"), AccountStatus.ACTIVE, Instant.now(), 2);
        users.store.put(user.id(), user);
        tokens.store.put("hash:raw", AccountToken.invitation(user.id(), "hash:raw", Instant.now().plus(Duration.ofHours(1))));

        assertThatThrownBy(() -> handler().handle(new Command("raw", "segredo1")))
                .isInstanceOf(IllegalStateException.class);
    }

    private static final class FakeUsers implements SecurityUserRepository {
        final Map<UserId, SecurityUser> store = new HashMap<>();

        @Override public boolean existsByNormalizedEmail(String normalizedEmail) { return false; }
        @Override public Optional<SecurityUser> findById(UserId id) { return Optional.ofNullable(store.get(id)); }
        @Override public java.util.List<SecurityUser> findPage(int page, int size) { return java.util.List.copyOf(store.values()); }
        @Override public long count() { return store.size(); }
        @Override public void save(SecurityUser user) { store.put(user.id(), user); }
    }

    private static final class FakeTokens implements AccountTokenRepository {
        final Map<String, AccountToken> store = new HashMap<>();

        @Override public void save(AccountToken token) { store.put(token.tokenHash(), token); }
        @Override public Optional<AccountToken> findInvitationByHash(String tokenHash) {
            return Optional.ofNullable(store.get(tokenHash));
        }
    }

    private static final class FakeCredentials implements PasswordCredentialRepository {
        final Map<UserId, PasswordCredential> store = new HashMap<>();

        @Override public void save(PasswordCredential credential) { store.put(credential.userId(), credential); }
        @Override public Optional<PasswordCredential> findByUserId(UserId userId) {
            return Optional.ofNullable(store.get(userId));
        }
    }
}
