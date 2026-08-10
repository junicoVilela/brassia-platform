package br.com.brew.brassia.security.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.security.application.port.inbound.SsoLoginUseCase;
import br.com.brew.brassia.security.application.port.outbound.ExternalIdentityRepository;
import br.com.brew.brassia.security.application.port.outbound.FederatedIdentityProvider;
import br.com.brew.brassia.security.application.port.outbound.FederationProviderRepository;
import br.com.brew.brassia.security.application.port.outbound.SecurityUserRepository;
import br.com.brew.brassia.security.application.port.outbound.SsoHandshakeRepository;
import br.com.brew.brassia.security.application.service.SsoLoginHandler;
import br.com.brew.brassia.security.domain.EmailAddress;
import br.com.brew.brassia.security.domain.InvalidSsoHandshakeException;
import br.com.brew.brassia.security.domain.SecurityUser;
import br.com.brew.brassia.security.domain.SsoHandshake;
import br.com.brew.brassia.security.domain.UserId;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O login federado de ponta a ponta do lado da aplicação (SEC-B07).
 *
 * <p>O dublê do provedor existe porque os cenários que mais importam — resposta com nonce de outra conversa,
 * provedor devolvendo um e-mail que já pertence a uma conta local — são justamente os que nenhum IdP real
 * produz sob encomenda.
 */
class SsoLoginHandlerTest {

    private static final UUID CERVEJARIA = UUID.randomUUID();
    private static final UUID PROVEDOR = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-08-09T10:00:00Z");

    private FakeProviders providers;
    private FakeHandshakes handshakes;
    private FakeIdentityProvider identityProvider;
    private FakeIdentities identities;
    private FakeUsers users;
    private RecordingAudit audit;
    private SsoLoginHandler handler;

    @BeforeEach
    void setUp() {
        providers = new FakeProviders();
        handshakes = new FakeHandshakes();
        identityProvider = new FakeIdentityProvider();
        identities = new FakeIdentities();
        users = new FakeUsers();
        audit = new RecordingAudit();
        providers.save(view("ACTIVE", true));
        handler = handlerAt(AGORA);
    }

    private SsoLoginHandler handlerAt(Instant now) {
        return new SsoLoginHandler(providers, handshakes, identityProvider, identities, users, audit,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private static FederationProviderRepository.ProviderView view(String status, boolean jit) {
        return new FederationProviderRepository.ProviderView(PROVEDOR, CERVEJARIA, "okta", "Okta", "OIDC",
                status, "https://idp.example.com", null, Map.of(), jit, 0L);
    }

    private String startAndGetState() {
        return handler.start(new SsoLoginUseCase.StartCommand(CERVEJARIA, "okta", "/production/batches"))
                .state();
    }

    @Test
    @DisplayName("start abre o aperto de mão, grava e devolve a URL do provedor")
    void startAbreHandshake() {
        var start = handler.start(new SsoLoginUseCase.StartCommand(CERVEJARIA, "okta", "/x"));

        assertThat(start.authorizationUri().toString()).contains("idp.example.com");
        assertThat(handshakes.stored).hasSize(1);
        assertThat(start.state()).isEqualTo(handshakes.stored.getFirst().state());
    }

    @Test
    @DisplayName("provedor desativado e inexistente dão a mesma resposta")
    void provedorIndisponivel() {
        // Distinguir contaria a quem sonda quais provedores a cervejaria tem configurados.
        providers.save(view("DISABLED", true));
        assertThatThrownBy(() -> handler.start(new SsoLoginUseCase.StartCommand(CERVEJARIA, "okta", "/")))
                .isInstanceOf(InvalidSsoHandshakeException.class);

        assertThatThrownBy(() -> handler.start(
                new SsoLoginUseCase.StartCommand(CERVEJARIA, "nao-existe", "/")))
                .isInstanceOf(InvalidSsoHandshakeException.class);
    }

    @Test
    @DisplayName("volta com vínculo existente autentica e leva ao destino guardado na ida")
    void voltaComVinculo() {
        var ana = UUID.randomUUID();
        var state = startAndGetState();
        identities.links.put(PROVEDOR + "|sub-ana", ana);
        identityProvider.respondWith("sub-ana", "ana@cervejaria.com", true, "Ana");

        var completion = handler.complete(new SsoLoginUseCase.CallbackCommand(state, Map.of()));

        assertThat(completion.userId()).isEqualTo(ana);
        assertThat(completion.provisioned()).isFalse();
        assertThat(completion.redirectAfterLogin()).isEqualTo("/production/batches");
    }

    @Test
    @DisplayName("SEQUESTRO BARRADO: conta local de mesmo e-mail sem vínculo é recusada e AUDITADA")
    void sequestroBarrado() {
        // O ataque: quem controla ou engana um provedor configurado afirma o e-mail de um administrador.
        var state = startAndGetState();
        users.byEmail.put("admin@cervejaria.com", UUID.randomUUID());
        identityProvider.respondWith("sub-atacante", "admin@cervejaria.com", true, "Admin");

        assertThatThrownBy(() -> handler.complete(new SsoLoginUseCase.CallbackCommand(state, Map.of())))
                .isInstanceOf(SsoLoginHandler.SsoLinkRefusedException.class);

        // Nada foi criado nem vinculado.
        assertThat(users.saved).isEmpty();
        assertThat(identities.links).doesNotContainKey(PROVEDOR + "|sub-atacante");

        // E a tentativa fica no registro: se tivesse passado, seria um sequestro de conta.
        var recusa = audit.events.stream().filter(e -> e.action().equals("security.sso.refused"))
                .findFirst().orElseThrow();
        assertThat(recusa.metadata()).containsEntry("reason", "CONTA_LOCAL_EXISTENTE");
        assertThat(recusa.metadata()).containsEntry("assertedEmail", "admin@cervejaria.com");
    }

    @Test
    @DisplayName("sem conta nenhuma, com JIT e e-mail verificado: cria conta ativa e vincula")
    void provisiona() {
        var state = startAndGetState();
        identityProvider.respondWith("sub-novo", "novo@cervejaria.com", true, "Pessoa Nova");

        var completion = handler.complete(new SsoLoginUseCase.CallbackCommand(state, Map.of()));

        assertThat(completion.provisioned()).isTrue();
        assertThat(users.saved).hasSize(1);
        assertThat(identities.links).containsKey(PROVEDOR + "|sub-novo");
        assertThat(audit.events).anyMatch(e -> e.action().equals("security.sso.provision"));
    }

    @Test
    @DisplayName("e-mail não verificado não provisiona, mesmo com JIT ligado")
    void emailNaoVerificadoNaoProvisiona() {
        // Sem verificação, quem consegue um cadastro no provedor escolhe o e-mail com que aparece aqui.
        var state = startAndGetState();
        identityProvider.respondWith("sub-novo", "novo@cervejaria.com", false, "X");

        assertThatThrownBy(() -> handler.complete(new SsoLoginUseCase.CallbackCommand(state, Map.of())))
                .isInstanceOf(SsoLoginHandler.SsoLinkRefusedException.class);
        assertThat(users.saved).isEmpty();
    }

    @Test
    @DisplayName("provedor sem JIT não cria conta")
    void semJitNaoProvisiona() {
        providers.save(view("ACTIVE", false));
        var state = startAndGetState();
        identityProvider.respondWith("sub-novo", "novo@cervejaria.com", true, "X");

        assertThatThrownBy(() -> handler.complete(new SsoLoginUseCase.CallbackCommand(state, Map.of())))
                .isInstanceOf(SsoLoginHandler.SsoLinkRefusedException.class);
    }

    @Test
    @DisplayName("state desconhecido é recusado antes de tocar no provedor")
    void stateDesconhecido() {
        assertThatThrownBy(() -> handler.complete(
                new SsoLoginUseCase.CallbackCommand("inventado", Map.of())))
                .isInstanceOf(InvalidSsoHandshakeException.class);

        // O provedor nem foi consultado: a ordem é aperto de mão primeiro.
        assertThat(identityProvider.verifyCalls).isZero();
    }

    @Test
    @DisplayName("USO ÚNICO: a mesma volta não autentica duas vezes")
    void usoUnico() {
        var ana = UUID.randomUUID();
        var state = startAndGetState();
        identities.links.put(PROVEDOR + "|sub-ana", ana);
        identityProvider.respondWith("sub-ana", "ana@cervejaria.com", true, "Ana");

        handler.complete(new SsoLoginUseCase.CallbackCommand(state, Map.of()));

        assertThatThrownBy(() -> handler.complete(new SsoLoginUseCase.CallbackCommand(state, Map.of())))
                .isInstanceOf(InvalidSsoHandshakeException.class);
    }

    @Test
    @DisplayName("aperto de mão vencido é recusado")
    void vencido() {
        var state = startAndGetState();
        identityProvider.respondWith("sub-ana", "ana@cervejaria.com", true, "Ana");

        var tarde = handlerAt(AGORA.plus(Duration.ofMinutes(11)));

        assertThatThrownBy(() -> tarde.complete(new SsoLoginUseCase.CallbackCommand(state, Map.of())))
                .isInstanceOf(InvalidSsoHandshakeException.class);
        assertThat(identityProvider.verifyCalls).isZero();
    }

    @Test
    @DisplayName("a verificação do provedor acontece ANTES da decisão de vínculo")
    void verificacaoAntesDoVinculo() {
        // Decidir o vínculo antes de verificar seria acreditar num e-mail que ninguém provou ter vindo do
        // provedor.
        var state = startAndGetState();
        identityProvider.failVerification();
        users.byEmail.put("admin@cervejaria.com", UUID.randomUUID());

        assertThatThrownBy(() -> handler.complete(new SsoLoginUseCase.CallbackCommand(state, Map.of())))
                .isInstanceOf(InvalidSsoHandshakeException.class);

        // Nem a recusa de vínculo chegou a ser avaliada.
        assertThat(audit.events).noneMatch(e -> e.action().equals("security.sso.refused"));
    }

    @Test
    @DisplayName("o vínculo existente ignora o e-mail asserido agora")
    void vinculoIgnoraEmailAtual() {
        // Uma pessoa pode ter trocado de e-mail no provedor sem deixar de ser a mesma pessoa.
        var ana = UUID.randomUUID();
        var state = startAndGetState();
        identities.links.put(PROVEDOR + "|sub-ana", ana);
        users.byEmail.put("outro@cervejaria.com", UUID.randomUUID());
        identityProvider.respondWith("sub-ana", "outro@cervejaria.com", true, "Ana");

        assertThat(handler.complete(new SsoLoginUseCase.CallbackCommand(state, Map.of())).userId())
                .isEqualTo(ana);
    }

    // --- dublês ---

    private static final class FakeProviders implements FederationProviderRepository {
        private ProviderView current;

        void save(ProviderView view) {
            this.current = view;
        }

        @Override public UUID create(UUID b, String c, String d, String p, String i, Map<String, Object> cfg) {
            return PROVEDOR;
        }

        @Override public Optional<ProviderView> findById(UUID id) {
            return Optional.ofNullable(current).filter(v -> v.id().equals(id));
        }

        @Override public List<ProviderView> listByBrewery(UUID breweryId) {
            return current == null || !current.breweryId().equals(breweryId) ? List.of() : List.of(current);
        }

    }

    private static final class FakeHandshakes implements SsoHandshakeRepository {
        private final List<SsoHandshake> stored = new ArrayList<>();

        @Override public void insert(SsoHandshake handshake) {
            stored.add(handshake);
        }

        @Override public Optional<SsoHandshake> byState(String state) {
            return stored.stream().filter(h -> h.state().equals(state)).findFirst();
        }

        @Override public boolean markConsumed(SsoHandshake handshake) {
            for (int i = 0; i < stored.size(); i++) {
                if (stored.get(i).id().equals(handshake.id())) {
                    if (stored.get(i).consumed()) {
                        return false;
                    }
                    stored.set(i, handshake);
                    return true;
                }
            }
            return false;
        }
    }

    private static final class FakeIdentityProvider implements FederatedIdentityProvider {
        private AssertedIdentity next;
        private boolean fail;
        int verifyCalls;

        void respondWith(String subject, String email, boolean verified, String name) {
            this.next = new AssertedIdentity(subject, email, verified, name);
        }

        void failVerification() {
            this.fail = true;
        }

        @Override public URI authorizationUri(ProviderConfig config, SsoHandshake handshake) {
            return URI.create(config.issuerOrEntityId() + "/authorize?state=" + handshake.state()
                    + "&code_challenge=" + handshake.codeChallenge());
        }

        @Override public AssertedIdentity verify(ProviderConfig config, SsoHandshake handshake,
                Map<String, String> callback) {
            verifyCalls++;
            if (fail) {
                throw new InvalidSsoHandshakeException("resposta do provedor não confere");
            }
            return next;
        }
    }

    private static final class FakeIdentities implements ExternalIdentityRepository {
        private final Map<String, UUID> links = new HashMap<>();

        @Override public void link(UUID providerId, UUID userId, String subject, String email) {
            links.put(providerId + "|" + subject, userId);
        }

        @Override public Optional<UUID> resolveUserId(UUID providerId, String subject) {
            return Optional.ofNullable(links.get(providerId + "|" + subject));
        }

        @Override public List<IdentityView> listByProvider(UUID providerId) {
            return List.of();
        }
    }

    private static final class FakeUsers implements SecurityUserRepository {
        private final Map<String, UUID> byEmail = new HashMap<>();
        private final List<SecurityUser> saved = new ArrayList<>();

        @Override public Optional<SecurityUser> findByNormalizedEmail(String email) {
            return Optional.ofNullable(byEmail.get(email)).map(id -> SecurityUser.reconstitute(
                    new UserId(id), new EmailAddress(email), new br.com.brew.brassia.security.domain.DisplayName("Existente"),
                    br.com.brew.brassia.security.domain.AccountStatus.ACTIVE, AGORA, 0L));
        }

        @Override public Optional<SecurityUser> findById(UserId id) {
            return Optional.empty();
        }

        @Override public void save(SecurityUser user) {
            saved.add(user);
        }

        @Override public boolean existsByNormalizedEmail(String email) {
            return byEmail.containsKey(email);
        }

        @Override public List<SecurityUser> findPage(int page, int size) {
            return List.of();
        }

        @Override public long count() {
            return byEmail.size();
        }
    }

    private static final class RecordingAudit implements AuditTrail {
        private final List<AuditEvent> events = new ArrayList<>();

        @Override public void record(AuditEvent event) {
            events.add(event);
        }
    }
}
