package br.com.brew.brassia.inventory.application.port.outbound;

import br.com.brew.brassia.inventory.domain.StockLotProperty;
import java.util.List;
import java.util.UUID;

public interface StockLotPropertyRepository {
    void insert(StockLotProperty property);

    /** Revisão atual (mais recente) de cada propriedade do lote — "última vale" (STK-005-A). */
    List<StockLotProperty> findCurrentByLot(UUID breweryId, UUID lotId);

    /** Todas as revisões do lote (histórico), da mais recente para a mais antiga por propriedade. */
    List<StockLotProperty> findHistoryByLot(UUID breweryId, UUID lotId);
}
