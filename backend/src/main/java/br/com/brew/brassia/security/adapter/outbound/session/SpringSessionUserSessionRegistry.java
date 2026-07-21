package br.com.brew.brassia.security.adapter.outbound.session;

import br.com.brew.brassia.security.application.port.outbound.UserSessionRegistry;
import br.com.brew.brassia.security.domain.UserId;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/**
 * Revoga sessões via Spring Session (repositório indexado por nome do principal;
 * por convenção do projeto, esse nome é o id do usuário, definido no login —
 * SEC-002). O repositório indexado é opcional: enquanto o login/sessões não
 * existirem, a revogação é um no-op seguro, sem quebrar a desativação.
 */
@Component
class SpringSessionUserSessionRegistry implements UserSessionRegistry {
    private final ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> sessions;

    SpringSessionUserSessionRegistry(
            ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> sessions) {
        this.sessions = sessions;
    }

    @Override
    public void revokeAll(UserId userId) {
        var repository = sessions.getIfAvailable();
        if (repository == null) {
            return;
        }
        repository.findByPrincipalName(userId.value().toString()).keySet()
                .forEach(repository::deleteById);
    }
}
