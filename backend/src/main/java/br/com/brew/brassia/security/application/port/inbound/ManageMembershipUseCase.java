package br.com.brew.brassia.security.application.port.inbound;

import java.util.UUID;

/** Associa/desassocia um usuário a um grupo, sempre na cervejaria ativa do ator. */
public interface ManageMembershipUseCase {
    void grant(Command command);
    void revoke(Command command);

    /**
     * @param actorId      administrador autenticado (auditoria)
     * @param breweryId    cervejaria ativa (autoridade do escopo — não vem do corpo)
     * @param targetUserId usuário alvo
     * @param groupId      grupo
     */
    record Command(UUID actorId, UUID breweryId, UUID targetUserId, UUID groupId) {}
}
