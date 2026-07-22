package br.com.brew.brassia.security.application.service;

import br.com.brew.brassia.security.application.port.inbound.ManageOwnSessionsUseCase;
import br.com.brew.brassia.security.application.port.outbound.UserSessionCatalog;
import br.com.brew.brassia.security.domain.UserId;
import java.util.List;
import java.util.Objects;

/**
 * Lista e revoga sessões do próprio usuário. O id completo nunca sai da aplicação:
 * a listagem devolve apenas uma {@code ref} mascarada (prefixo).
 */
public final class ManageOwnSessionsHandler implements ManageOwnSessionsUseCase {
    private static final int REF_LENGTH = 8;

    private final UserSessionCatalog sessions;

    public ManageOwnSessionsHandler(UserSessionCatalog sessions) {
        this.sessions = Objects.requireNonNull(sessions);
    }

    @Override
    public List<SessionView> list(UserId userId, String currentSessionId) {
        var current = currentSessionId == null ? "" : currentSessionId;
        return sessions.list(userId).stream()
                .map(s -> new SessionView(ref(s.id()), s.createdAt(), s.lastAccessedAt(), s.id().equals(current)))
                .toList();
    }

    @Override
    public void revokeByRef(UserId userId, String ref) {
        sessions.revokeByRef(userId, ref);
    }

    @Override
    public void revokeOthers(UserId userId, String currentSessionId) {
        sessions.revokeOthers(userId, currentSessionId == null ? "" : currentSessionId);
    }

    private static String ref(String sessionId) {
        return sessionId.length() <= REF_LENGTH ? sessionId : sessionId.substring(0, REF_LENGTH);
    }
}
