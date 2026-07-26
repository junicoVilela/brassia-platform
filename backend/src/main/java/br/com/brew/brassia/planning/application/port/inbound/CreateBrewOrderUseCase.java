package br.com.brew.brassia.planning.application.port.inbound;

import java.math.BigDecimal;
import java.util.UUID;

public interface CreateBrewOrderUseCase {
    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID recipeId, BigDecimal volumeLiters) {}

    record Result(UUID id, String code, String status) {}
}
