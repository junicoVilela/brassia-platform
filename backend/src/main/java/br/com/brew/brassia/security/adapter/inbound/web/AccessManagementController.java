package br.com.brew.brassia.security.adapter.inbound.web;

import br.com.brew.brassia.security.application.port.inbound.AccessCatalogQuery;
import br.com.brew.brassia.security.application.port.inbound.ManageMembershipUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/security")
final class AccessManagementController {
    private final AccessCatalogQuery catalog;
    private final ManageMembershipUseCase manageMembership;

    AccessManagementController(AccessCatalogQuery catalog, ManageMembershipUseCase manageMembership) {
        this.catalog = catalog;
        this.manageMembership = manageMembership;
    }

    @GetMapping("/permissions")
    List<AccessCatalogQuery.PermissionView> permissions(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("security.permission.read");
        return catalog.permissions();
    }

    @GetMapping("/groups")
    List<AccessCatalogQuery.GroupView> groups(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("security.group.read");
        return catalog.groups();
    }

    @PostMapping("/users/{userId}/memberships")
    ResponseEntity<Void> grant(@PathVariable UUID userId, @Valid @RequestBody MembershipRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("security.membership.manage");
        manageMembership.grant(command(principal, userId, request.groupId()));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/users/{userId}/memberships/{groupId}")
    ResponseEntity<Void> revoke(@PathVariable UUID userId, @PathVariable UUID groupId,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("security.membership.manage");
        manageMembership.revoke(command(principal, userId, groupId));
        return ResponseEntity.noContent().build();
    }

    // brewery_id é a cervejaria ativa do principal, nunca do corpo.
    private static ManageMembershipUseCase.Command command(SecurityPrincipal principal, UUID userId, UUID groupId) {
        return new ManageMembershipUseCase.Command(principal.userId(), principal.requireBrewery(), userId, groupId);
    }

    record MembershipRequest(@NotNull UUID groupId) {}
}
