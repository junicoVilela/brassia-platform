package br.com.brew.brassia.recipe.application.port.inbound;

import java.util.UUID;

public interface PublishRecipeUseCase {
    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID recipeId) {}

    record Result(UUID id, String name, String status, long version) {}
}
