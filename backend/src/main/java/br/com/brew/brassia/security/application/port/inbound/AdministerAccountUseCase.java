package br.com.brew.brassia.security.application.port.inbound;

import java.util.UUID;

/**
 * Administração do ciclo da conta: bloquear, desbloquear e desativar. Cada
 * operação é uma transição de status distinta, auditada individualmente.
 */
@FunctionalInterface
public interface AdministerAccountUseCase {
    Result handle(Command command);

    enum Operation { BLOCK, UNBLOCK, DISABLE }

    /**
     * @param actorId      administrador autenticado (auditoria)
     * @param breweryId    cervejaria do contexto (auditoria)
     * @param targetUserId conta alvo da operação
     * @param operation    transição a aplicar
     */
    record Command(UUID actorId, UUID breweryId, UUID targetUserId, Operation operation) {}

    record Result(UUID userId, String status) {}
}
