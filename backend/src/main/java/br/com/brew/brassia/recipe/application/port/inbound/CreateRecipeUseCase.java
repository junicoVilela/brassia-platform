package br.com.brew.brassia.recipe.application.port.inbound;

import java.util.UUID;

@FunctionalInterface
public interface CreateRecipeUseCase {
    Result handle(Command command);

    record Command(UUID breweryId, String name) {}
    record Result(UUID id, String name, String status) {}
}
