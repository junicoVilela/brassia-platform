package br.com.brew.brassia.security.application.service;

import br.com.brew.brassia.security.application.port.inbound.LoginHistoryQuery;
import br.com.brew.brassia.security.application.port.outbound.LoginEventRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class LoginHistoryQueryHandler implements LoginHistoryQuery {
    private final LoginEventRepository loginEvents;

    public LoginHistoryQueryHandler(LoginEventRepository loginEvents) {
        this.loginEvents = Objects.requireNonNull(loginEvents);
    }

    @Override
    public List<LoginEventView> recentByUser(UUID userId, int limit) {
        return loginEvents.recentByUser(userId, limit).stream()
                .map(e -> new LoginEventView(e.occurredAt(), e.outcome(), e.reasonCode()))
                .toList();
    }
}
