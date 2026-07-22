package br.com.brew.brassia.security.application.port.inbound;

import java.util.UUID;

/**
 * Orquestra o login por credenciais internas: limita tentativas (SEC-012),
 * autentica e registra o resultado no histórico (SEC-006). A borda HTTP cuida
 * apenas de sessão, cookies e MFA pendente; por isso os dados de origem chegam
 * como valores simples (nada de tipos de servlet nesta camada).
 */
public interface PerformLoginUseCase {
    Result handle(Command command);

    /**
     * @param ip        IP de origem, usado no limite e no histórico; pode ser nulo
     * @param userAgent user-agent da requisição, registrado no histórico
     * @param traceId   correlação para auditoria/observabilidade
     */
    record Command(String email, String password, String ip, String userAgent, String traceId) {}

    record Result(UUID userId, String displayName, boolean mfaRequired) {}
}
