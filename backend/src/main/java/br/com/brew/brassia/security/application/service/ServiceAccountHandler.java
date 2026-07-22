package br.com.brew.brassia.security.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.security.application.port.inbound.AuthenticateApiKeyUseCase;
import br.com.brew.brassia.security.application.port.inbound.ManageServiceAccountUseCase;
import br.com.brew.brassia.security.application.port.outbound.ApiCredentialRepository;
import br.com.brew.brassia.security.application.port.outbound.ServiceAccountRepository;
import br.com.brew.brassia.security.application.port.outbound.TokenHasher;
import br.com.brew.brassia.shared.security.ForbiddenException;
import br.com.brew.brassia.shared.security.ServicePrincipal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Contas de serviço e autenticação por API key (SEC-011). */
public final class ServiceAccountHandler {
    private static final String KEY_PREFIX = "brassia_";
    private static final int KEY_BYTES = 32;

    private final ServiceAccountRepository accounts;
    private final ApiCredentialRepository credentials;
    private final TokenHasher tokenHasher;
    private final AuditTrail audit;
    private final SecureRandom random = new SecureRandom();

    public ServiceAccountHandler(
            ServiceAccountRepository accounts,
            ApiCredentialRepository credentials,
            TokenHasher tokenHasher,
            AuditTrail audit) {
        this.accounts = Objects.requireNonNull(accounts);
        this.credentials = Objects.requireNonNull(credentials);
        this.tokenHasher = Objects.requireNonNull(tokenHasher);
        this.audit = Objects.requireNonNull(audit);
    }

    public ManageServiceAccountUseCase.ServiceAccountView create(ManageServiceAccountUseCase.CreateCommand command) {
        var id = accounts.create(command.breweryId(), command.code().toUpperCase(), command.name());
        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "security.service-account.create",
                "service_account", id.toString(), Map.of("code", command.code())));
        return accounts.findById(id).map(this::toView).orElseThrow();
    }

    public List<ManageServiceAccountUseCase.ServiceAccountView> list(ManageServiceAccountUseCase.ListCommand command) {
        return accounts.listByBrewery(command.breweryId()).stream().map(this::toView).toList();
    }

    public ManageServiceAccountUseCase.IssueCredentialResult issueCredential(ManageServiceAccountUseCase.IssueCommand command) {
        if (!accounts.belongsToBrewery(command.serviceAccountId(), command.breweryId())) {
            throw new ForbiddenException("conta de serviço de outra cervejaria");
        }
        var rawKey = KEY_PREFIX + randomKey();
        var prefix = rawKey.substring(0, Math.min(16, rawKey.length()));
        var hash = tokenHasher.hash(rawKey);
        var id = credentials.issue(command.serviceAccountId(), prefix, hash, command.scopes(), null);
        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "security.api-credential.issue",
                "api_credential", id.toString(), Map.of("prefix", prefix)));
        return new ManageServiceAccountUseCase.IssueCredentialResult(id, rawKey, prefix);
    }

    public void revokeCredential(ManageServiceAccountUseCase.RevokeCommand command) {
        credentials.revoke(command.credentialId());
        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "security.api-credential.revoke",
                "api_credential", command.credentialId().toString(), Map.of()));
    }

    public Optional<ServicePrincipal> authenticate(String rawKey) {
        if (rawKey == null || !rawKey.startsWith(KEY_PREFIX)) {
            return Optional.empty();
        }
        var prefix = rawKey.substring(0, Math.min(16, rawKey.length()));
        var candidate = credentials.findActiveByPrefix(prefix).orElse(null);
        if (candidate == null) {
            return Optional.empty();
        }
        var hash = tokenHasher.hash(rawKey);
        if (!hash.equals(candidate.keyHash())) {
            return Optional.empty();
        }
        credentials.touchLastUsed(candidate.id(), Instant.now());
        return Optional.of(new ServicePrincipal(
                candidate.serviceAccountId(), candidate.breweryId(), Set.copyOf(candidate.scopes())));
    }

    private ManageServiceAccountUseCase.ServiceAccountView toView(ServiceAccountRepository.ServiceAccountView view) {
        return new ManageServiceAccountUseCase.ServiceAccountView(view.id(), view.code(), view.name(), view.active());
    }

    private String randomKey() {
        var bytes = new byte[KEY_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
