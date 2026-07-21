package br.com.brew.brassia.security.application.port.outbound;

import br.com.brew.brassia.security.domain.UserId;
import java.time.Instant;
import java.util.List;

/** Lista e revoga as sessões ativas de um usuário (Spring Session, indexado por principal). */
public interface UserSessionCatalog {
    List<ActiveSession> list(UserId userId);
    /** Revoga a sessão do usuário cujo id começa por {@code ref}; ignora se não for dele. */
    void revokeByRef(UserId userId, String ref);
    /** Revoga todas as sessões do usuário, exceto a de id {@code currentSessionId}. */
    void revokeOthers(UserId userId, String currentSessionId);

    record ActiveSession(String id, Instant createdAt, Instant lastAccessedAt) {}
}
