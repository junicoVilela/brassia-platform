package br.com.brew.brassia.security.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.security.application.port.inbound.SsoLoginUseCase;
import br.com.brew.brassia.security.application.port.outbound.ExternalIdentityRepository;
import br.com.brew.brassia.security.application.port.outbound.FederatedIdentityProvider;
import br.com.brew.brassia.security.application.port.outbound.FederationProviderRepository;
import br.com.brew.brassia.security.application.port.outbound.SecurityUserRepository;
import br.com.brew.brassia.security.application.port.outbound.SsoHandshakeRepository;
import br.com.brew.brassia.security.domain.AccountLinkDecision;
import br.com.brew.brassia.security.domain.DisplayName;
import br.com.brew.brassia.security.domain.EmailAddress;
import br.com.brew.brassia.security.domain.InvalidSsoHandshakeException;
import br.com.brew.brassia.security.domain.SecurityUser;
import br.com.brew.brassia.security.domain.SsoHandshake;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Login federado iniciado pelo nosso lado (SEC-B07).
 *
 * <p><strong>A ordem das verificações é a segurança da história.</strong> Primeiro o aperto de mão — state,
 * validade, uso único —, depois a verificação do provedor (nonce, PKCE, assinatura), e só então a decisão
 * de vínculo. Inverter qualquer par significaria confiar em algo antes de ter direito: decidir o vínculo
 * antes de verificar a resposta seria acreditar num e-mail que ninguém provou ter vindo do provedor.
 *
 * <p><strong>O que este caso de uso recusa é tão importante quanto o que ele aceita.</strong> A recusa mais
 * relevante não é de credencial errada: é a de vincular uma identidade externa a uma conta local que já
 * existe. Ver {@link AccountLinkDecision}.
 */
public final class SsoLoginHandler implements SsoLoginUseCase {

    private final FederationProviderRepository providers;
    private final SsoHandshakeRepository handshakes;
    private final FederatedIdentityProvider identityProvider;
    private final ExternalIdentityRepository identities;
    private final SecurityUserRepository users;
    private final AuditTrail audit;
    private final Clock clock;

    public SsoLoginHandler(FederationProviderRepository providers, SsoHandshakeRepository handshakes,
            FederatedIdentityProvider identityProvider, ExternalIdentityRepository identities,
            SecurityUserRepository users, AuditTrail audit, Clock clock) {
        this.providers = Objects.requireNonNull(providers, "providers");
        this.handshakes = Objects.requireNonNull(handshakes, "handshakes");
        this.identityProvider = Objects.requireNonNull(identityProvider, "identityProvider");
        this.identities = Objects.requireNonNull(identities, "identities");
        this.users = Objects.requireNonNull(users, "users");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Start start(StartCommand command) {
        Objects.requireNonNull(command, "command");

        var provider = activeProvider(command.breweryId(), command.providerCode());
        var handshake = SsoHandshake.open(provider.id(), command.redirectAfterLogin(), clock.instant());
        handshakes.insert(handshake);

        return new Start(identityProvider.authorizationUri(toConfig(provider), handshake), handshake.state());
    }

    @Override
    public Completion complete(CallbackCommand command) {
        Objects.requireNonNull(command, "command");

        // 1. O aperto de mão. Sem ele, nada do que veio na volta tem a quem se ligar.
        var stored = handshakes.byState(command.state())
                .orElseThrow(() -> new InvalidSsoHandshakeException("state desconhecido"));
        var consumed = stored.consumeWith(command.state(), clock.instant());

        // O uso único é decidido pelo banco: duas voltas simultâneas com a mesma resposta passariam as duas
        // por uma checagem feita só em memória.
        if (!handshakes.markConsumed(consumed)) {
            throw new InvalidSsoHandshakeException("aperto de mão já utilizado");
        }

        var provider = providers.findById(consumed.providerId())
                .orElseThrow(() -> new InvalidSsoHandshakeException("provedor indisponível"));

        // 2. A verificação do provedor: nonce, PKCE, assinatura. Só depois disso o que veio é informação.
        var asserted = identityProvider.verify(toConfig(provider), consumed, command.parameters());

        // 3. A decisão de vínculo, sobre uma identidade já verificada.
        var email = AccountLinkDecision.normalizeEmail(asserted.email());
        var existingLink = identities.resolveUserId(provider.id(), asserted.subject());
        var localAccount = users.findByNormalizedEmail(email).map(u -> u.id().value());

        var decision = AccountLinkDecision.decide(existingLink, localAccount, provider.jitMode(),
                asserted.emailVerified());

        return switch (decision.outcome()) {
            case LINK_EXISTS -> {
                var userId = decision.userId().orElseThrow();
                auditLogin(provider, userId, asserted.subject(), "LINK_EXISTS");
                yield new Completion(userId, consumed.redirectAfterLogin(), false);
            }
            case PROVISION -> {
                var userId = provision(provider, asserted, email);
                yield new Completion(userId, consumed.redirectAfterLogin(), true);
            }
            case REFUSE_WOULD_HIJACK -> {
                // Auditado como recusa, e com o motivo: é o registro de uma tentativa que, se tivesse
                // passado, seria um sequestro de conta. Quem investiga precisa encontrá-la.
                auditRefusal(provider, asserted.subject(), email,
                        localAccount.isPresent() ? "CONTA_LOCAL_EXISTENTE" : "JIT_OU_EMAIL_NAO_VERIFICADO");
                throw new SsoLinkRefusedException(localAccount.isPresent());
            }
        };
    }

    /**
     * Cria a conta e o vínculo (JIT).
     *
     * <p>A conta nasce <strong>ativa e sem senha local</strong>. Sem senha porque ela não tem: quem entra
     * por federação entra pelo provedor, e uma senha vazia ou aleatória seria uma credencial que ninguém
     * conhece mas que existe para ser adivinhada. A conta nasce sem cervejaria — o acesso é concedido por
     * quem administra, e é isso que impede o provisionamento automático de virar concessão automática.
     */
    private UUID provision(FederationProviderRepository.ProviderView provider,
            FederatedIdentityProvider.AssertedIdentity asserted, String email) {
        var displayName = asserted.displayName() == null || asserted.displayName().isBlank()
                ? email : asserted.displayName().trim();
        var user = SecurityUser.activeAccount(new EmailAddress(email), new DisplayName(displayName),
                clock.instant());
        users.save(user);

        var userId = user.id().value();
        identities.link(provider.id(), userId, asserted.subject(), email);

        var metadata = new LinkedHashMap<String, String>();
        metadata.put("provider", provider.code());
        metadata.put("subject", asserted.subject());
        metadata.put("outcome", "PROVISION");
        audit.record(AuditEvent.success(provider.breweryId(), userId, "security.sso.provision",
                "security_user", userId.toString(), metadata));
        return userId;
    }

    private FederationProviderRepository.ProviderView activeProvider(UUID breweryId, String code) {
        return providers.listByBrewery(breweryId).stream()
                .filter(p -> p.code().equalsIgnoreCase(code))
                .filter(p -> "ACTIVE".equals(p.status()))
                // Provedor inexistente e provedor desativado dão a mesma resposta: distinguir contaria a
                // quem sonda quais provedores a cervejaria tem configurados.
                .findFirst()
                .orElseThrow(() -> new InvalidSsoHandshakeException("provedor indisponível"));
    }

    private FederatedIdentityProvider.ProviderConfig toConfig(
            FederationProviderRepository.ProviderView provider) {
        return new FederatedIdentityProvider.ProviderConfig(provider.id(), provider.breweryId(),
                provider.code(), provider.protocol(), provider.issuerOrEntityId(), provider.configuration(),
                provider.jitMode());
    }

    private void auditLogin(FederationProviderRepository.ProviderView provider, UUID userId, String subject,
            String outcome) {
        var metadata = new LinkedHashMap<String, String>();
        metadata.put("provider", provider.code());
        metadata.put("subject", subject);
        metadata.put("outcome", outcome);
        audit.record(AuditEvent.success(provider.breweryId(), userId, "security.sso.login",
                "security_user", userId.toString(), metadata));
    }

    private void auditRefusal(FederationProviderRepository.ProviderView provider, String subject,
            String email, String reason) {
        var metadata = new LinkedHashMap<String, String>();
        metadata.put("provider", provider.code());
        metadata.put("subject", subject);
        // O e-mail entra porque é o dado da tentativa e quem investiga precisa dele. Não é segredo — foi
        // o próprio provedor que o asseriu.
        metadata.put("assertedEmail", email);
        metadata.put("outcome", "REFUSED");
        metadata.put("reason", reason);
        audit.record(AuditEvent.success(provider.breweryId(), null, "security.sso.refused",
                "federation_provider", provider.id().toString(), metadata));
    }

    /** Recusa de vínculo: existe conta local, ou o provedor não pode criar contas. */
    public static final class SsoLinkRefusedException extends RuntimeException {

        private final boolean localAccountExists;

        SsoLinkRefusedException(boolean localAccountExists) {
            super("vínculo recusado");
            this.localAccountExists = localAccountExists;
        }

        public boolean localAccountExists() {
            return localAccountExists;
        }
    }
}
