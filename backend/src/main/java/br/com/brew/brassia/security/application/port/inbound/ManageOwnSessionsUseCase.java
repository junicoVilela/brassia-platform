package br.com.brew.brassia.security.application.port.inbound;

import br.com.brew.brassia.security.domain.UserId;
import java.time.Instant;
import java.util.List;

/** Self-service de sessões do usuário autenticado. */
public interface ManageOwnSessionsUseCase {
    List<SessionView> list(UserId userId, String currentSessionId);

    void revokeByRef(UserId userId, String ref);

    void revokeOthers(UserId userId, String currentSessionId);

    record SessionView(String ref, Instant createdAt, Instant lastAccessedAt, boolean current) {}
}
