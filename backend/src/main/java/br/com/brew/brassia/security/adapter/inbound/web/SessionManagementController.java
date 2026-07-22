package br.com.brew.brassia.security.adapter.inbound.web;

import br.com.brew.brassia.security.application.port.inbound.ManageOwnSessionsUseCase;
import br.com.brew.brassia.security.domain.UserId;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sessões do próprio usuário (self-service). O id de sessão nunca é exposto: a
 * aplicação devolve uma {@code ref} mascarada e resolve a revogação pelo prefixo.
 */
@RestController
@RequestMapping("/api/v1/security/sessions")
final class SessionManagementController {
    private final ManageOwnSessionsUseCase sessions;

    SessionManagementController(ManageOwnSessionsUseCase sessions) {
        this.sessions = sessions;
    }

    @GetMapping
    List<ManageOwnSessionsUseCase.SessionView> list(
            @AuthenticationPrincipal SecurityPrincipal principal, HttpServletRequest request) {
        return sessions.list(new UserId(principal.userId()), currentSessionId(request));
    }

    @DeleteMapping("/{ref}")
    ResponseEntity<Void> revoke(@PathVariable String ref, @AuthenticationPrincipal SecurityPrincipal principal) {
        sessions.revokeByRef(new UserId(principal.userId()), ref);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    ResponseEntity<Void> revokeOthers(
            @AuthenticationPrincipal SecurityPrincipal principal, HttpServletRequest request) {
        sessions.revokeOthers(new UserId(principal.userId()), currentSessionId(request));
        return ResponseEntity.noContent().build();
    }

    private static String currentSessionId(HttpServletRequest request) {
        var session = request.getSession(false);
        return session == null ? "" : session.getId();
    }
}
