package br.com.brew.brassia.production.adapter.inbound.event;

import br.com.brew.brassia.planning.BrewOrderStarted;
import br.com.brew.brassia.production.application.port.inbound.OpenBatchUseCase;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Abre o lote de produção ao consumir {@link BrewOrderStarted} (PRD-001), de forma
 * síncrona — roda no mesmo commit do início da OP. Idempotente por OP.
 */
@Component
class BrewOrderStartedListener {

    private final OpenBatchUseCase openBatch;

    BrewOrderStartedListener(OpenBatchUseCase openBatch) {
        this.openBatch = openBatch;
    }

    @EventListener
    void on(BrewOrderStarted event) {
        openBatch.handle(new OpenBatchUseCase.Command(event.breweryId(), event.orderId(), event.code(),
                event.recipeId(), event.recipeVersion(), event.recipeName(), event.volumeLiters(),
                event.actorId()));
    }
}
