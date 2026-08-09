package br.com.brew.brassia.digitaltwin.application.port.inbound;

import br.com.brew.brassia.digitaltwin.domain.LearnedProfile;
import java.util.List;
import java.util.UUID;

/** Cálculo de perfil aprendido (DTW-001). */
public interface ProfileCommands {

    LearnedProfile compute(Request request);

    /**
     * @param batchIds a amostra, <strong>informada e não descoberta</strong>. Quem conhece a operação pode
     *                 excluir o lote em que a bomba falhou, e a exclusão fica visível no perfil em vez de
     *                 escondida dentro de uma consulta. Cada lote é resolvido pelas consultas publicadas
     *                 da produção — um lote de outra cervejaria simplesmente não resolve.
     */
    record Request(UUID actorId, UUID breweryId, UUID recipeId, List<UUID> batchIds) {
    }
}
