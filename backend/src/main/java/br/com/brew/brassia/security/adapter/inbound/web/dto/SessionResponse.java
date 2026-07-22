package br.com.brew.brassia.security.adapter.inbound.web.dto;

import br.com.brew.brassia.security.application.port.inbound.ResolveSessionContextUseCase.SessionContext;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record SessionResponse(
        UUID userId,
        String displayName,
        BreweryView activeBrewery,
        List<BreweryView> accessibleBreweries,
        Set<String> permissions) implements LoginResponse {

    /** Monta a resposta a partir do principal autenticado e do contexto resolvido. */
    public static SessionResponse from(SecurityPrincipal principal, SessionContext context) {
        var accessible = context.accessibleBreweries().stream().map(BreweryView::from).toList();
        var active = context.accessibleBreweries().stream()
                .filter(b -> b.id().equals(context.activeBreweryId()))
                .findFirst().map(BreweryView::from).orElse(null);
        return new SessionResponse(
                principal.userId(), principal.displayName(), active, accessible, principal.permissions());
    }
}
