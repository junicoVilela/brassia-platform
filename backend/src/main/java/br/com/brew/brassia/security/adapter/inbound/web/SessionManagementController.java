package br.com.brew.brassia.security.adapter.inbound.web;

import br.com.brew.brassia.security.application.port.outbound.UserSessionCatalog;
import br.com.brew.brassia.security.domain.UserId;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
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
 * listagem devolve uma {@code ref} mascarada (prefixo) e a revogação resolve a
 * ref para o id real entre as sessões do usuário.
 */
@RestController
@RequestMapping("/api/v1/security/sessions")
final class SessionManagementController {
    private static final int REF_LENGTH = 8;

    private final UserSessionCatalog sessions;

    SessionManagementController(UserSessionCatalog sessions) {
        this.sessions = sessions;
    }

    @GetMapping
    List<SessionView> list(@AuthenticationPrincipal SecurityPrincipal principal, HttpServletRequest request) {
        var currentId = currentSessionId(request);
        return sessions.list(new UserId(principal.userId())).stream()
                .map(s -> new SessionView(ref(s.id()), s.createdAt(), s.lastAccessedAt(), s.id().equals(currentId)))
                .toList();
    }

    @DeleteMapping("/{ref}")
    ResponseEntity<Void> revoke(@PathVariable String ref, @AuthenticationPrincipal SecurityPrincipal principal) {
        sessions.revokeByRef(new UserId(principal.userId()), ref);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    ResponseEntity<Void> revokeOthers(@AuthenticationPrincipal SecurityPrincipal principal, HttpServletRequest request) {
        sessions.revokeOthers(new UserId(principal.userId()), currentSessionId(request));
        return ResponseEntity.noContent().build();
    }

    private static String ref(String sessionId) {
        return sessionId.length() <= REF_LENGTH ? sessionId : sessionId.substring(0, REF_LENGTH);
    }

    private static String currentSessionId(HttpServletRequest request) {
        var session = request.getSession(false);
        return session == null ? "" : session.getId();
    }

    record SessionView(String ref, Instant createdAt, Instant lastAccessedAt, boolean current) {}
}
