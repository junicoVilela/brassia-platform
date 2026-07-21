package br.com.brew.brassia.security.application.port.inbound;

import java.util.UUID;

@FunctionalInterface
public interface InviteUserUseCase {
    Result handle(Command command);

    /**
     * @param actorId   usuário autenticado que emite o convite (auditoria)
     * @param breweryId cervejaria do contexto autenticado (auditoria/autorização)
     * @param email     e-mail do convidado
     * @param displayName nome de exibição do convidado
     */
    record Command(UUID actorId, UUID breweryId, String email, String displayName) {}

    record Result(UUID userId, String email, String status) {}
}
