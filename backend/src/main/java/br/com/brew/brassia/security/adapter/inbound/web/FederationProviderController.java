package br.com.brew.brassia.security.adapter.inbound.web;

import br.com.brew.brassia.security.adapter.inbound.web.dto.CreateFederationProviderRequest;
import br.com.brew.brassia.security.adapter.inbound.web.dto.LinkExternalIdentityRequest;
import br.com.brew.brassia.security.application.port.inbound.ManageFederationProviderUseCase;
import br.com.brew.brassia.security.application.port.outbound.FederationProviderRepository;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/security/federation-providers")
final class FederationProviderController {
    private final ManageFederationProviderUseCase federation;

    FederationProviderController(ManageFederationProviderUseCase federation) {
        this.federation = federation;
    }

    @GetMapping
    List<FederationProviderRepository.ProviderView> list(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("security.federation.read");
        return federation.list(principal.requireBrewery());
    }

    @PostMapping
    ResponseEntity<Map<String, UUID>> create(
            @Valid @RequestBody CreateFederationProviderRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("security.federation.manage");
        var id = federation.create(new ManageFederationProviderUseCase.CreateCommand(
                principal.requireBrewery(), principal.userId(), request.code(), request.displayName(),
                request.protocol(), request.issuerOrEntityId(), request.configuration()));
        return ResponseEntity.created(URI.create("/api/v1/security/federation-providers/" + id))
                .body(Map.of("id", id));
    }

    @PostMapping("/{id}/validate")
    ResponseEntity<Void> validate(@PathVariable UUID id, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("security.federation.validate");
        federation.validate(new ManageFederationProviderUseCase.ValidateCommand(
                principal.requireBrewery(), principal.userId(), id));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/identities")
    ResponseEntity<Void> link(
            @PathVariable UUID id,
            @Valid @RequestBody LinkExternalIdentityRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("security.federation.manage");
        federation.linkIdentity(new ManageFederationProviderUseCase.LinkCommand(
                principal.requireBrewery(), principal.userId(), id, request.userId(),
                request.externalSubject(), request.normalizedEmail()));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/resolve")
    Map<String, UUID> resolve(
            @PathVariable UUID id,
            @org.springframework.web.bind.annotation.RequestParam String subject,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("security.federation.read");
        return Map.of("userId", federation.resolveUserId(id, subject));
    }
}
