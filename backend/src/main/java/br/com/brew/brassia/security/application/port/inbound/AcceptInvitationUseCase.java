package br.com.brew.brassia.security.application.port.inbound;

import java.util.UUID;

@FunctionalInterface
public interface AcceptInvitationUseCase {
    Result handle(Command command);

    /** @param rawToken token de convite recebido pelo convidado (valor bruto) */
    record Command(String rawToken) {}

    record Result(UUID userId, String status) {}
}
