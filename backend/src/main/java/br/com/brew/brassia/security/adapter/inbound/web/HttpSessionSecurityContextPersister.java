package br.com.brew.brassia.security.adapter.inbound.web;

import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/** Persiste o {@link SecurityPrincipal} no SecurityContext da sessão HTTP. */
@Component
final class HttpSessionSecurityContextPersister {
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();
    private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();

    void persist(SecurityPrincipal principal, HttpServletRequest request, HttpServletResponse response,
            boolean rotate) {
        var context = holder.createEmptyContext();
        context.setAuthentication(new SecurityPrincipalAuthentication(principal));
        holder.setContext(context);
        request.getSession(true);
        if (rotate) {
            request.changeSessionId();
        }
        contextRepository.saveContext(context, request, response);
    }

    void clear(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        holder.clearContext();
    }
}
