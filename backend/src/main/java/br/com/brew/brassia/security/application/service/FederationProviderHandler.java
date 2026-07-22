package br.com.brew.brassia.security.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.security.application.port.inbound.ManageFederationProviderUseCase;
import br.com.brew.brassia.security.application.port.outbound.ExternalIdentityRepository;
import br.com.brew.brassia.security.application.port.outbound.FederationProviderRepository;
import br.com.brew.brassia.shared.security.ForbiddenException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** CRUD e validação de provedores SAML/OIDC (SEC-014/015). */
public final class FederationProviderHandler {
    private final FederationProviderRepository providers;
    private final ExternalIdentityRepository identities;
    private final SamlAssertionValidator samlValidator;
    private final OidcTokenClaimsValidator oidcValidator;
    private final AuditTrail audit;

    public FederationProviderHandler(
            FederationProviderRepository providers,
            ExternalIdentityRepository identities,
            SamlAssertionValidator samlValidator,
            OidcTokenClaimsValidator oidcValidator,
            AuditTrail audit) {
        this.providers = Objects.requireNonNull(providers);
        this.identities = Objects.requireNonNull(identities);
        this.samlValidator = Objects.requireNonNull(samlValidator);
        this.oidcValidator = Objects.requireNonNull(oidcValidator);
        this.audit = Objects.requireNonNull(audit);
    }

    public UUID create(ManageFederationProviderUseCase.CreateCommand command) {
        var id = providers.create(command.breweryId(), command.code(), command.displayName(),
                command.protocol(), command.issuerOrEntityId(), command.configuration());
        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "security.federation.create",
                "federation_provider", id.toString(), Map.of("protocol", command.protocol())));
        return id;
    }

    public List<FederationProviderRepository.ProviderView> list(UUID breweryId) {
        return providers.listByBrewery(breweryId);
    }

    public void validate(ManageFederationProviderUseCase.ValidateCommand command) {
        var provider = providers.findById(command.providerId())
                .orElseThrow(() -> new IllegalArgumentException("provedor inexistente"));
        if (!provider.breweryId().equals(command.breweryId())) {
            throw new ForbiddenException("provedor de outra cervejaria");
        }
        switch (provider.protocol()) {
            case "SAML" -> validateSaml(provider);
            case "OIDC" -> validateOidc(provider);
            default -> throw new IllegalArgumentException("protocolo inválido");
        }
        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "security.federation.validate",
                "federation_provider", provider.id().toString(), Map.of()));
    }

    public void linkIdentity(ManageFederationProviderUseCase.LinkCommand command) {
        var provider = providers.findById(command.providerId())
                .orElseThrow(() -> new IllegalArgumentException("provedor inexistente"));
        if (!provider.breweryId().equals(command.breweryId())) {
            throw new ForbiddenException("provedor de outra cervejaria");
        }
        identities.link(command.providerId(), command.userId(), command.externalSubject(), command.normalizedEmail());
        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "security.federation.link",
                "external_identity", command.externalSubject(), Map.of("userId", command.userId().toString())));
    }

    public UUID resolveUserId(UUID providerId, String externalSubject) {
        return identities.resolveUserId(providerId, externalSubject)
                .orElseThrow(() -> new IllegalArgumentException("identidade externa não vinculada"));
    }

    private void validateSaml(FederationProviderRepository.ProviderView provider) {
        if (provider.issuerOrEntityId() == null || provider.issuerOrEntityId().isBlank()) {
            throw new IllegalArgumentException("issuer SAML obrigatório");
        }
        var cert = (String) provider.configuration().get("signingCertPem");
        if (cert == null) {
            throw new IllegalArgumentException("certificado de assinatura SAML obrigatório");
        }
        samlValidator.validateSigningCertificatePem(cert);
    }

    private void validateOidc(FederationProviderRepository.ProviderView provider) {
        var clientId = (String) provider.configuration().get("clientId");
        oidcValidator.validateProviderConfig(provider.issuerOrEntityId(), clientId);
    }
}
