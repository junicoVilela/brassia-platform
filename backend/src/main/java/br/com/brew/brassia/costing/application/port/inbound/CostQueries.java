package br.com.brew.brassia.costing.application.port.inbound;

import br.com.brew.brassia.costing.domain.BatchCost;
import java.util.List;
import java.util.UUID;

/** Leituras do custo (CST-001). */
public interface CostQueries {

    /** Custos já fechados da cervejaria. */
    List<BatchCost> closed(UUID breweryId);

    /**
     * O custo do lote: o fechado, se já houver; senão a soma de agora, derivada do ledger.
     *
     * @throws br.com.brew.brassia.costing.domain.UnknownBatchCostException lote inexistente
     */
    BatchCost ofBatch(UUID breweryId, UUID batchId);
}
