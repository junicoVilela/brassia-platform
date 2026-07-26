package br.com.brew.brassia.inventory.application.port.outbound;

import br.com.brew.brassia.inventory.StockReserved;

/** Publica eventos de domínio do estoque para outros módulos. */
public interface StockEventPublisher {
    void publish(StockReserved event);
}
