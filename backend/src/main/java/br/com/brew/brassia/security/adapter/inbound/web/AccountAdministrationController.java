package br.com.brew.brassia.security.adapter.inbound.web;

import br.com.brew.brassia.security.application.port.inbound.AdministerAccountUseCase;
import br.com.brew.brassia.security.application.port.inbound.AdministerAccountUseCase.Operation;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/security/users/{userId}")
final class AccountAdministrationController {
    private final AdministerAccountUseCase administerAccount;

    AccountAdministrationController(AdministerAccountUseCase administerAccount) {
        this.administerAccount = administerAccount;
    }

    @PostMapping("/block")
    ResponseEntity<Response> block(@PathVariable UUID userId, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("security.user.block");
        return apply(principal, userId, Operation.BLOCK);
    }

    @PostMapping("/unblock")
    ResponseEntity<Response> unblock(@PathVariable UUID userId, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("security.user.block");
        return apply(principal, userId, Operation.UNBLOCK);
    }

    @PostMapping("/disable")
    ResponseEntity<Response> disable(@PathVariable UUID userId, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("security.user.disable");
        return apply(principal, userId, Operation.DISABLE);
    }

    private ResponseEntity<Response> apply(SecurityPrincipal principal, UUID userId, Operation operation) {
        var result = administerAccount.handle(new AdministerAccountUseCase.Command(
                principal.userId(), principal.breweryId(), userId, operation));
        return ResponseEntity.ok(new Response(result.userId(), result.status()));
    }

    record Response(UUID userId, String status) {}
}
