package br.com.brew.brassia.security.adapter.inbound.web;

import br.com.brew.brassia.security.adapter.inbound.web.dto.CreateServiceAccountRequest;
import br.com.brew.brassia.security.adapter.inbound.web.dto.IssueCredentialRequest;
import br.com.brew.brassia.security.adapter.inbound.web.dto.IssueCredentialResponse;
import br.com.brew.brassia.security.adapter.inbound.web.dto.ServiceAccountResponse;
import br.com.brew.brassia.security.application.port.inbound.ManageServiceAccountUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import br.com.brew.brassia.shared.security.ServicePrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
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
@RequestMapping("/api/v1/security/service-accounts")
final class ServiceAccountController {
    private final ManageServiceAccountUseCase manage;

    ServiceAccountController(ManageServiceAccountUseCase manage) {
        this.manage = manage;
    }

    @GetMapping("/me")
    ServiceAccountResponse me(@AuthenticationPrincipal ServicePrincipal principal) {
        return new ServiceAccountResponse(
                principal.serviceAccountId(), principal.breweryId(), "api-key", true, List.of());
    }

    @GetMapping
    List<ServiceAccountResponse> list(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("security.service-account.read");
        return manage.list(new ManageServiceAccountUseCase.ListCommand(principal.requireBrewery()))
                .stream().map(ServiceAccountResponse::from).toList();
    }

    @PostMapping
    ResponseEntity<ServiceAccountResponse> create(
            @Valid @RequestBody CreateServiceAccountRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("security.service-account.manage");
        var result = manage.create(new ManageServiceAccountUseCase.CreateCommand(
                principal.userId(), principal.requireBrewery(), request.code(), request.name()));
        return ResponseEntity.created(URI.create("/api/v1/security/service-accounts/" + result.id()))
                .body(ServiceAccountResponse.from(result));
    }

    @PostMapping("/{id}/credentials")
    IssueCredentialResponse issue(
            @PathVariable UUID id,
            @Valid @RequestBody IssueCredentialRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("security.api-credential.issue");
        var result = manage.issueCredential(new ManageServiceAccountUseCase.IssueCommand(
                principal.userId(), principal.requireBrewery(), id, request.scopes()));
        return new IssueCredentialResponse(result.credentialId(), result.rawKey(), result.keyPrefix());
    }

    @PostMapping("/credentials/{credentialId}/revoke")
    ResponseEntity<Void> revoke(
            @PathVariable UUID credentialId,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("security.api-credential.revoke");
        manage.revokeCredential(new ManageServiceAccountUseCase.RevokeCommand(
                principal.userId(), principal.requireBrewery(), credentialId));
        return ResponseEntity.noContent().build();
    }
}
