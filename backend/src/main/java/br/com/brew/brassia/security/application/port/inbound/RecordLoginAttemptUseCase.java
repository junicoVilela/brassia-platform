package br.com.brew.brassia.security.application.port.inbound;

import java.util.UUID;

/** Registra tentativa de login (sucesso ou falha) no histórico. */
@FunctionalInterface
public interface RecordLoginAttemptUseCase {
    void record(Command command);

    enum Outcome { SUCCESS, FAILURE }

    /**
     * @param userId     usuário (nulo em falha, para não vazar existência)
     * @param identifier e-mail informado
     */
    record Command(
            UUID userId,
            String identifier,
            Outcome outcome,
            String reasonCode,
            String ip,
            String userAgent,
            String traceId) {}
}
