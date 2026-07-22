package br.com.brew.brassia.security.adapter.inbound.web;

import br.com.brew.brassia.security.adapter.inbound.web.dto.TemporaryAccessGrantRequest;
import br.com.brew.brassia.security.application.port.inbound.TemporaryAccessQuery;
import br.com.brew.brassia.security.application.port.inbound.TemporaryAccessUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
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

/** Acesso temporário (SEC-008): concessão com vigência, aprovação e revogação. */
@RestController
@RequestMapping("/api/v1/security/temporary-access")
final class TemporaryAccessController {
    private final TemporaryAccessUseCase temporaryAccess;
    private final TemporaryAccessQuery query;

    TemporaryAccessController(TemporaryAccessUseCase temporaryAccess, TemporaryAccessQuery query) {
        this.temporaryAccess = temporaryAccess;
        this.query = query;
    }

    @GetMapping
    List<TemporaryAccessQuery.GrantView> list(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("security.temporary-access.read");
        return query.current(principal.requireBrewery());
    }

    @PostMapping
    ResponseEntity<Map<String, UUID>> request(@Valid @RequestBody TemporaryAccessGrantRequest body,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("security.temporary-access.request");
        // brewery e solicitante vêm do principal, nunca do corpo.
        var id = temporaryAccess.request(new TemporaryAccessUseCase.RequestCommand(
                principal.userId(), principal.requireBrewery(), body.userId(),
                body.permissionCode(), body.reason(), body.durationHours()));
        return ResponseEntity.created(URI.create("/api/v1/security/temporary-access/" + id))
                .body(Map.of("id", id));
    }

    @PostMapping("/{id}/approve")
    ResponseEntity<Void> approve(@PathVariable UUID id, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("security.temporary-access.approve");
        temporaryAccess.approve(id, principal.userId(), principal.requireBrewery());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> revoke(@PathVariable UUID id, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("security.temporary-access.revoke");
        temporaryAccess.revoke(id, principal.userId(), principal.requireBrewery());
        return ResponseEntity.noContent().build();
    }
}
