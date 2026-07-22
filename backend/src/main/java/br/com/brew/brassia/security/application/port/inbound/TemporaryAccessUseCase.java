package br.com.brew.brassia.security.application.port.inbound;

import java.util.UUID;

/**
 * Concede, aprova e revoga acesso temporário. O escopo é sempre a cervejaria
 * ativa do ator (autoridade do escopo — nunca vem do corpo da requisição).
 */
public interface TemporaryAccessUseCase {

    /** Solicita a concessão e devolve o id criado. */
    UUID request(RequestCommand command);

    /** Aprova uma concessão pendente (aprovador ≠ solicitante). */
    void approve(UUID grantId, UUID actorId, UUID breweryId);

    /** Revoga uma concessão. */
    void revoke(UUID grantId, UUID actorId, UUID breweryId);

    /**
     * @param actorId       administrador autenticado (solicitante e auditoria)
     * @param breweryId     cervejaria ativa (escopo da concessão)
     * @param targetUserId  usuário que recebe o acesso
     * @param permissionCode código da permissão concedida
     * @param reason        justificativa (obrigatória)
     * @param durationHours duração da vigência a partir de agora, em horas
     */
    record RequestCommand(UUID actorId, UUID breweryId, UUID targetUserId,
            String permissionCode, String reason, int durationHours) {}
}
