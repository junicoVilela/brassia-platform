package br.com.brew.brassia.catalog.application.port.inbound;

import java.util.Map;
import java.util.UUID;

public interface UpdateIngredientUseCase {
    Result handle(Command command);

    /**
     * @param version versão esperada para o lock otimista; divergência é conflito
     */
    record Command(UUID actorId, UUID breweryId, UUID ingredientId, String name, String useUnit,
            String purchaseUnit, Map<String, String> attributes, long version) {}

    record Result(UUID id, String type, String code, String name, String useUnit, String purchaseUnit,
            Map<String, String> attributes, boolean active, long version) {}
}
