package br.com.brew.brassia.recipe.application.port.inbound;

import java.math.BigDecimal;
import java.util.UUID;

public interface ScaleRecipeUseCase {
    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID recipeId, String name, BigDecimal batchVolumeLiters) {}

    record Result(UUID id, String name, String status, long version, BigDecimal batchVolumeLiters) {}
}
