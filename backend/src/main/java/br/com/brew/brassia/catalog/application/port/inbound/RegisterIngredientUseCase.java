package br.com.brew.brassia.catalog.application.port.inbound;

import java.util.Map;
import java.util.UUID;

public interface RegisterIngredientUseCase {
    Result handle(Command command);

    /**
     * @param actorId      usuário autenticado (auditoria)
     * @param breweryId    cervejaria do contexto (tenant)
     * @param attributes   atributos específicos do tipo; pode ser nulo
     */
    record Command(UUID actorId, UUID breweryId, String type, String code, String name,
            String useUnit, String purchaseUnit, Map<String, String> attributes) {}

    record Result(UUID id, String type, String code, String name, String useUnit, String purchaseUnit,
            Map<String, String> attributes, boolean active, long version) {}
}
