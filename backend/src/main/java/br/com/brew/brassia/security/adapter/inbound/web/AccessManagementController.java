package br.com.brew.brassia.security.adapter.inbound.web;

import br.com.brew.brassia.audit.AuditQuery;
import br.com.brew.brassia.security.adapter.inbound.web.dto.AuditEventPageResponse;
import br.com.brew.brassia.security.adapter.inbound.web.dto.CreateGroupRequest;
import br.com.brew.brassia.security.adapter.inbound.web.dto.GroupResponse;
import br.com.brew.brassia.security.adapter.inbound.web.dto.MembershipRequest;
import br.com.brew.brassia.security.adapter.inbound.web.dto.UpdateGroupRequest;
import br.com.brew.brassia.security.application.port.inbound.AccessCatalogQuery;
import br.com.brew.brassia.security.application.port.inbound.ManageGroupUseCase;
import br.com.brew.brassia.security.application.port.inbound.ManageMembershipUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/security")
final class AccessManagementController {
    private final AccessCatalogQuery catalog;
    private final ManageMembershipUseCase manageMembership;
    private final ManageGroupUseCase manageGroup;
    private final AuditQuery auditQuery;

    AccessManagementController(
            AccessCatalogQuery catalog,
            ManageMembershipUseCase manageMembership,
            ManageGroupUseCase manageGroup,
            AuditQuery auditQuery) {
        this.catalog = catalog;
        this.manageMembership = manageMembership;
        this.manageGroup = manageGroup;
        this.auditQuery = auditQuery;
    }

    @GetMapping("/audit-events")
    AuditEventPageResponse auditEvents(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("security.audit.read");
        var result = auditQuery.search(new AuditQuery.SearchCriteria(
                principal.requireBrewery(), action, targetType, actorId, outcome,
                parseInstant(from), parseInstant(to), page, size));
        return AuditEventPageResponse.from(result);
    }

    private static java.time.Instant parseInstant(String value) {
        return value == null || value.isBlank() ? null : java.time.Instant.parse(value.trim());
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

    @PostMapping("/groups")
    ResponseEntity<GroupResponse> createGroup(
            @Valid @RequestBody CreateGroupRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("security.group.manage");
        var result = manageGroup.create(new ManageGroupUseCase.CreateCommand(
                principal.userId(),
                principal.requireBrewery(),
                principal.permissions(),
                request.code(),
                request.name(),
                request.description(),
                request.permissionCodes()));
        return ResponseEntity.created(URI.create("/api/v1/security/groups/" + result.id()))
                .body(GroupResponse.from(result));
    }

    @PatchMapping("/groups/{groupId}")
    GroupResponse updateGroup(
            @PathVariable UUID groupId,
            @Valid @RequestBody UpdateGroupRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("security.group.manage");
        var result = manageGroup.update(new ManageGroupUseCase.UpdateCommand(
                principal.userId(),
                principal.requireBrewery(),
                principal.permissions(),
                groupId,
                request.name(),
                request.description(),
                request.permissionCodes(),
                request.version()));
        return GroupResponse.from(result);
    }

    @GetMapping("/users/{userId}/memberships")
    List<AccessCatalogQuery.MembershipView> memberships(@PathVariable UUID userId,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("security.membership.manage");
        return catalog.membershipsByUser(principal.requireBrewery(), userId);
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

    private static ManageMembershipUseCase.Command command(SecurityPrincipal principal, UUID userId, UUID groupId) {
        return new ManageMembershipUseCase.Command(principal.userId(), principal.requireBrewery(), userId, groupId);
    }

}
