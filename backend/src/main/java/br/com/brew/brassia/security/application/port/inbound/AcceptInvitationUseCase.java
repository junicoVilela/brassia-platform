package br.com.brew.brassia.security.application.port.inbound;

import java.util.UUID;

@FunctionalInterface
public interface AcceptInvitationUseCase {
    Result handle(Command command);

    /**
     * @param rawToken token de convite recebido pelo convidado (valor bruto)
     * @param password senha escolhida pelo convidado (define a credencial)
     */
    record Command(String rawToken, String password) {}

    record Result(UUID userId, String status) {}
}
