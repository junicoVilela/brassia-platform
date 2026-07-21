package br.com.brew.brassia.security.adapter.inbound.web;

import br.com.brew.brassia.security.application.port.inbound.InviteUserUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/security/users")
final class SecurityUserController {
    private final InviteUserUseCase inviteUser;

    SecurityUserController(InviteUserUseCase inviteUser) {
        this.inviteUser = inviteUser;
    }

    @PostMapping
    ResponseEntity<Response> invite(
            @Valid @RequestBody Request request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("security.user.invite");
        var result = inviteUser.handle(new InviteUserUseCase.Command(
                principal.userId(), principal.breweryId(), request.email(), request.displayName()));
        return ResponseEntity.created(URI.create("/api/v1/security/users/" + result.userId()))
                .body(new Response(result.userId(), result.email(), result.status()));
    }

    record Request(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 160) String displayName) {}

    record Response(UUID userId, String email, String status) {}
}
