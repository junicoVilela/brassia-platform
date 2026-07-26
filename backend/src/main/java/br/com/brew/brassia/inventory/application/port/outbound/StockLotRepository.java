package br.com.brew.brassia.inventory.application.port.outbound;

import br.com.brew.brassia.inventory.domain.StockLot;
import java.util.List;
import java.util.UUID;

public interface StockLotRepository {
    void insert(StockLot lot);

    List<StockLot> findAll(UUID breweryId);
}
