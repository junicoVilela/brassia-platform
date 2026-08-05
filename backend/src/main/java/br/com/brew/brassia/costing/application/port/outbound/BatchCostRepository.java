package br.com.brew.brassia.costing.application.port.outbound;

import br.com.brew.brassia.costing.domain.BatchCost;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistência do custo fechado (CST-001).
 *
 * <p>Só o fechado mora aqui: enquanto o custo está aberto, ele é derivado do ledger a cada leitura,
 * e não há linha nenhuma nestas tabelas.
 */
public interface BatchCostRepository {

    void insert(BatchCost cost);

    Optional<BatchCost> findByBatch(UUID breweryId, UUID batchId);

    List<BatchCost> findAll(UUID breweryId);
}
