package br.com.brew.brassia.security.application.port.outbound;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Histórico de tentativas de login. Valores identificáveis são pseudonimizados (hash). */
public interface LoginEventRepository {

    enum Outcome { SUCCESS, FAILURE }

    /**
     * @param userId     usuário (nulo em falha, para não vazar existência)
     * @param identifier identificador informado (e-mail); armazenado por hash
     * @param ip         IP de origem (nulo se ausente); armazenado por hash
     * @param userAgent  user-agent (nulo se ausente); armazenado por hash
     */
    void record(UUID userId, String identifier, Outcome outcome, String reasonCode,
            String ip, String userAgent, String traceId);

    List<LoginEventView> recentByUser(UUID userId, int limit);

    record LoginEventView(Instant occurredAt, String outcome, String reasonCode) {}
}
