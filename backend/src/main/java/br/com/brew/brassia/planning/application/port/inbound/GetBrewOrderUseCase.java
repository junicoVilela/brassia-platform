package br.com.brew.brassia.planning.application.port.inbound;

import br.com.brew.brassia.planning.domain.BrewOrder;
import java.util.UUID;

public interface GetBrewOrderUseCase {
    BrewOrder handle(UUID breweryId, UUID orderId);
}
