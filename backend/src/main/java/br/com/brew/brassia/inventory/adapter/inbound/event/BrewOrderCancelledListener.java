package br.com.brew.brassia.inventory.adapter.inbound.event;

import br.com.brew.brassia.inventory.application.port.inbound.ReleaseStockUseCase;
import br.com.brew.brassia.planning.BrewOrderCancelled;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Libera as reservas de estoque de uma OP cancelada (STK-003-B). Escuta de forma
 * síncrona o {@link BrewOrderCancelled} publicado pelo planejamento — roda no
 * mesmo commit do cancelamento. A liberação é idempotente (sem reservas → no-op).
 */
@Component
class BrewOrderCancelledListener {

    private final ReleaseStockUseCase releaseStock;

    BrewOrderCancelledListener(ReleaseStockUseCase releaseStock) {
        this.releaseStock = releaseStock;
    }

    @EventListener
    void on(BrewOrderCancelled event) {
        releaseStock.handle(new ReleaseStockUseCase.Command(
                event.actorId(), event.breweryId(), event.orderId()));
    }
}
