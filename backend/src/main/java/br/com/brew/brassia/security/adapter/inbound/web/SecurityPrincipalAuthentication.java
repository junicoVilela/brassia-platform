package br.com.brew.brassia.security.adapter.inbound.web;

import br.com.brew.brassia.shared.security.SecurityPrincipal;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;

/**
 * Authentication da sessão interna. {@link #getName()} devolve o id do usuário
 * para que o Spring Session indexe a sessão por usuário (habilita a revogação
 * de sessões — SEC-001). Autoridades ficam vazias: a autorização é resolvida no
 * caso de uso via {@link SecurityPrincipal#requirePermission(String)} (SEC-004).
 */
final class SecurityPrincipalAuthentication extends AbstractAuthenticationToken {
    private final SecurityPrincipal principal;

    SecurityPrincipalAuthentication(SecurityPrincipal principal) {
        super(List.of());
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    @Override
    public String getName() {
        return principal.userId().toString();
    }
}
