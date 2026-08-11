package br.com.brew.brassia.production.application.port.outbound;

import br.com.brew.brassia.production.domain.Batch;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BatchRepository {
    void insert(Batch batch);

    boolean existsByOrder(UUID breweryId, UUID orderId);

    /**
     * Uma página de lotes, mais recentes primeiro.
     *
     * <p>Substituiu o {@code findAll}: a listagem sem limite crescia com o histórico e cruzava a meta de
     * 500 ms por volta de 4.700 lotes (REL-002). Não há sobrecarga sem limite — deixar uma faria a
     * chamada antiga voltar por engano.
     */
    List<Batch> findPage(UUID breweryId, int offset, int limit);

    long countByBrewery(UUID breweryId);

    Optional<Batch> findById(UUID breweryId, UUID batchId);

    /**
     * Conclui a etapa ATIVA e ativa a próxima (PRD-002), atômico e guardado pelo
     * estado. Retorna {@code false} se a etapa não estava ativa (fora de ordem).
     */
    boolean completeStep(UUID breweryId, UUID batchId, UUID stepId, UUID nextStepId, Instant at);

    /**
     * Marca o lote como em fermentação (IN_PROGRESS → FERMENTING) na transferência
     * (PRD-005), guardado pelo estado. Retorna {@code false} se já não estava em
     * andamento (transferência única).
     */
    boolean markFermenting(UUID breweryId, UUID batchId, Instant at);

    /**
     * Encerra o lote que ficou sem volume (FERMENTING → COMPLETED), guardado pelo estado.
     *
     * <p>Retorna {@code false} quando o lote já não estava em fermentação. É a primeira conclusão de lote
     * da plataforma, e ela é consequência, não comando: ninguém "conclui" um lote — ele acaba quando a
     * cerveja acaba.
     */
    boolean markCompleted(UUID breweryId, UUID batchId, Instant at);
}
