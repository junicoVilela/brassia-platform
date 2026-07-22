package br.com.brew.brassia.security.adapter.inbound.web;

import br.com.brew.brassia.security.adapter.inbound.web.dto.AcceptInvitationRequest;
import br.com.brew.brassia.security.adapter.inbound.web.dto.AcceptInvitationResponse;
import br.com.brew.brassia.security.adapter.inbound.web.dto.InviteRequest;
import br.com.brew.brassia.security.adapter.inbound.web.dto.InviteResponse;
import br.com.brew.brassia.security.adapter.inbound.web.dto.UserSummaryResponse;
import br.com.brew.brassia.security.application.port.inbound.AcceptInvitationUseCase;
import br.com.brew.brassia.security.application.port.inbound.InviteUserUseCase;
import br.com.brew.brassia.security.application.port.inbound.ListUsersUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import br.com.brew.brassia.shared.web.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/security/users")
final class SecurityUserController {
    private final InviteUserUseCase inviteUser;
    private final AcceptInvitationUseCase acceptInvitation;
    private final ListUsersUseCase listUsers;

    SecurityUserController(InviteUserUseCase inviteUser, AcceptInvitationUseCase acceptInvitation,
            ListUsersUseCase listUsers) {
        this.inviteUser = inviteUser;
        this.acceptInvitation = acceptInvitation;
        this.listUsers = listUsers;
    }

    @GetMapping
    PageResponse<UserSummaryResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("security.user.read");
        var result = listUsers.handle(new ListUsersUseCase.Query(page, size));
        var content = result.content().stream()
                .map(s -> new UserSummaryResponse(s.id(), s.email(), s.displayName(), s.status(),
                        s.emailVerifiedAt() == null ? null : s.emailVerifiedAt().toString()))
                .toList();
        return new PageResponse<>(content, result.page(), result.size(), result.totalElements(), result.totalPages());
    }

    @PostMapping
    ResponseEntity<InviteResponse> invite(
            @Valid @RequestBody InviteRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("security.user.invite");
        var result = inviteUser.handle(new InviteUserUseCase.Command(
                principal.userId(), principal.breweryId(), request.email(), request.displayName()));
        return ResponseEntity.created(URI.create("/api/v1/security/users/" + result.userId()))
                .body(new InviteResponse(result.userId(), result.email(), result.status()));
    }

    // Público: o convidado ainda não tem sessão. Autenticado pelo token do convite.
    @PostMapping("/accept-invitation")
    ResponseEntity<AcceptInvitationResponse> acceptInvitation(@Valid @RequestBody AcceptInvitationRequest request) {
        var result = acceptInvitation.handle(new AcceptInvitationUseCase.Command(request.token(), request.password()));
        return ResponseEntity.ok(new AcceptInvitationResponse(result.userId(), result.status()));
    }
}
