package br.com.brew.brassia.costing.application.port.inbound;

import br.com.brew.brassia.costing.domain.BatchVariance;
import java.util.UUID;

/** Leitura do planejado versus real (CST-002). */
public interface VarianceQueries {

    /**
     * A explicação da diferença entre o custo que se esperava e o que aconteceu.
     *
     * <p>Sempre derivada, mesmo com o custo já fechado: o custo é a resposta daquele dia, a
     * explicação é sobre os fatos, e os fatos continuam sendo corrigidos depois do fechamento.
     *
     * @throws br.com.brew.brassia.costing.domain.UnknownBatchCostException lote inexistente
     */
    BatchVariance ofBatch(UUID breweryId, UUID batchId);
}
