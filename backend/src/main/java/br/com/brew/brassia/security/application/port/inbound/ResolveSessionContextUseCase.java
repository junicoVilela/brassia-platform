package br.com.brew.brassia.security.application.port.inbound;

import br.com.brew.brassia.brewery.BreweryRef;
import br.com.brew.brassia.security.domain.UserId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Resolve cervejarias acessíveis, ativa e permissões da sessão. */
@FunctionalInterface
public interface ResolveSessionContextUseCase {
    /**
     * @param requestedBreweryId cervejaria pedida (troca); nula usa a padrão
     */
    SessionContext resolve(UserId userId, UUID requestedBreweryId);

    /**
     * Contexto de sessão resolvido: cervejaria ativa, cervejarias acessíveis e as
     * permissões efetivas na cervejaria ativa.
     */
    record SessionContext(UUID activeBreweryId, List<BreweryRef> accessibleBreweries, Set<String> permissions) {}
}
