package br.com.brew.brassia.security.adapter.inbound.web;

import br.com.brew.brassia.security.application.port.inbound.AuthenticateApiKeyUseCase;
import br.com.brew.brassia.shared.security.ServicePrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Autentica API keys Bearer em rotas SCIM e health probe de service account. */
@Component
final class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
    private static final String BEARER = "Bearer ";

    private final AuthenticateApiKeyUseCase authenticateApiKey;

    ApiKeyAuthenticationFilter(AuthenticateApiKeyUseCase authenticateApiKey) {
        this.authenticateApiKey = authenticateApiKey;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        var path = request.getRequestURI();
        return !(path.startsWith("/scim/v2/") || path.equals("/api/v1/security/service-accounts/me"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER)) {
            var rawKey = header.substring(BEARER.length()).trim();
            authenticateApiKey.authenticate(rawKey).ifPresent(principal -> {
                var auth = new ServicePrincipalAuthentication(principal);
                SecurityContextHolder.getContext().setAuthentication(auth);
            });
        }
        chain.doFilter(request, response);
    }

    static final class ServicePrincipalAuthentication extends AbstractAuthenticationToken {
        private final ServicePrincipal principal;

        ServicePrincipalAuthentication(ServicePrincipal principal) {
            super(List.of());
            this.principal = principal;
            setAuthenticated(true);
        }

        @Override public Object getCredentials() { return null; }
        @Override public Object getPrincipal() { return principal; }
        @Override public String getName() { return principal.serviceAccountId().toString(); }
    }
}
