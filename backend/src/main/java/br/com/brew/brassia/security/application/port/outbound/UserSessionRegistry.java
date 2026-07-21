package br.com.brew.brassia.security.application.port.outbound;

import br.com.brew.brassia.security.domain.UserId;

/**
 * Revogação de sessões ativas de um usuário. Usado na desativação da conta para
 * derrubar o acesso imediatamente. A efetivação plena depende do fluxo de login
 * (SEC-002) que cria as sessões indexadas pelo id do usuário.
 */
public interface UserSessionRegistry {
    void revokeAll(UserId userId);
}
