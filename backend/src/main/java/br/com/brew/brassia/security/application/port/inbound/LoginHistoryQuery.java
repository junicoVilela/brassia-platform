package br.com.brew.brassia.security.application.port.inbound;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Consulta o histórico de tentativas de login do próprio usuário. */
@FunctionalInterface
public interface LoginHistoryQuery {
    List<LoginEventView> recentByUser(UUID userId, int limit);

    record LoginEventView(Instant occurredAt, String outcome, String reasonCode) {}
}
