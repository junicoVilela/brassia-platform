package br.com.brew.brassia.security.adapter.outbound.session;

import br.com.brew.brassia.security.application.port.outbound.UserSessionCatalog;
import br.com.brew.brassia.security.domain.UserId;
import java.util.List;
import java.util.Map;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/**
 * Catálogo de sessões sobre o Spring Session (indexado pelo nome do principal =
 * id do usuário, definido no login). Só opera sobre as sessões do próprio usuário.
 */
@Component
class SpringSessionUserSessionCatalog implements UserSessionCatalog {
    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    SpringSessionUserSessionCatalog(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    @Override
    public List<ActiveSession> list(UserId userId) {
        return ownSessions(userId).values().stream()
                .map(s -> new ActiveSession(s.getId(), s.getCreationTime(), s.getLastAccessedTime()))
                .toList();
    }

    @Override
    public void revokeByRef(UserId userId, String ref) {
        ownSessions(userId).keySet().stream()
                .filter(id -> id.startsWith(ref))
                .forEach(sessions::deleteById);
    }

    @Override
    public void revokeOthers(UserId userId, String currentSessionId) {
        ownSessions(userId).keySet().stream()
                .filter(id -> !id.equals(currentSessionId))
                .forEach(sessions::deleteById);
    }

    private Map<String, ? extends Session> ownSessions(UserId userId) {
        return sessions.findByPrincipalName(userId.value().toString());
    }
}
