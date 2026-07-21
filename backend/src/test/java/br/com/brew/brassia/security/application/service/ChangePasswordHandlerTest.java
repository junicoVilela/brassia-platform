package br.com.brew.brassia.security.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.security.application.port.inbound.ChangePasswordUseCase.Command;
import br.com.brew.brassia.security.application.port.outbound.PasswordCredentialRepository;
import br.com.brew.brassia.security.application.port.outbound.PasswordHasher;
import br.com.brew.brassia.security.application.port.outbound.PasswordHistoryRepository;
import br.com.brew.brassia.security.domain.PasswordCredential;
import br.com.brew.brassia.security.domain.UserId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChangePasswordHandlerTest {

    private final UUID userId = UUID.randomUUID();
    private final List<AuditEvent> audited = new ArrayList<>();
    private final AuditTrail audit = audited::add;
    private final FakeCredentials credentials = new FakeCredentials();
    private final FakeHistory history = new FakeHistory();
    private final Set<String> compromised = new HashSet<>();
    // hash "reversível" para o teste: enc:<senha>; matches compara o prefixo.
    private final PasswordHasher hasher = new PasswordHasher() {
        @Override public String hash(CharSequence raw) { return "enc:" + raw; }
        @Override public boolean matches(CharSequence raw, String encoded) { return encoded.equals("enc:" + raw); }
        @Override public boolean needsUpgrade(String encoded) { return false; }
    };
    private final PasswordPolicy policy = new PasswordPolicy(compromised::contains);

    private ChangePasswordHandler handler() {
        return new ChangePasswordHandler(credentials, history, hasher, policy, audit, 3);
    }

    private void withCurrent(String password) {
        credentials.store = new PasswordCredential(new UserId(userId), "enc:" + password, "delegating");
    }

    @Test
    void changesPasswordArchivesOldAndAudits() {
        withCurrent("segredo123");

        handler().handle(new Command(userId, "segredo123", "novaSenha456"));

        assertThat(credentials.store.passwordHash()).isEqualTo("enc:novaSenha456");
        assertThat(history.saved).singleElement().satisfies(c -> assertThat(c.passwordHash()).isEqualTo("enc:segredo123"));
        assertThat(audited).singleElement().satisfies(e -> assertThat(e.action()).isEqualTo("security.password.change"));
    }

    @Test
    void rejectsWrongCurrentPassword() {
        withCurrent("segredo123");

        assertThatThrownBy(() -> handler().handle(new Command(userId, "errada000", "novaSenha456")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(credentials.store.passwordHash()).isEqualTo("enc:segredo123");
    }

    @Test
    void rejectsCompromisedNewPassword() {
        withCurrent("segredo123");
        compromised.add("password1");

        assertThatThrownBy(() -> handler().handle(new Command(userId, "segredo123", "password1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsReuseOfCurrentOrRecent() {
        withCurrent("segredo123");
        assertThatThrownBy(() -> handler().handle(new Command(userId, "segredo123", "segredo123")))
                .isInstanceOf(IllegalArgumentException.class);

        history.hashes.add("enc:antiga12345");
        assertThatThrownBy(() -> handler().handle(new Command(userId, "segredo123", "antiga12345")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static final class FakeCredentials implements PasswordCredentialRepository {
        PasswordCredential store;
        @Override public void save(PasswordCredential credential) { store = credential; }
        @Override public Optional<PasswordCredential> findByUserId(UserId id) { return Optional.ofNullable(store); }
    }

    private static final class FakeHistory implements PasswordHistoryRepository {
        final List<PasswordCredential> saved = new ArrayList<>();
        final List<String> hashes = new ArrayList<>();
        @Override public void save(PasswordCredential replaced) { saved.add(replaced); }
        @Override public List<String> recentHashes(UserId userId, int limit) { return List.copyOf(hashes); }
    }
}
