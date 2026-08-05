package br.com.brew.brassia.packaging;

import java.util.List;
import java.util.UUID;

/**
 * Consulta publicada dos planos de envase de um lote (PKG-001), para quem precisa do recorte do
 * lote sem conhecer a tabela do envase — hoje o custo realizado (CST-001), que procura no ledger o
 * consumo de embalagem referente a cada plano.
 */
public interface PackagingPlanLookup {

    /** Planos do lote, inclusive os ainda não executados; lista vazia quando não houve envase. */
    List<UUID> plansOfBatch(UUID breweryId, UUID batchId);
}
