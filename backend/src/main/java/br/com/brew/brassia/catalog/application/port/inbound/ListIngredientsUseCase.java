package br.com.brew.brassia.catalog.application.port.inbound;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@FunctionalInterface
public interface ListIngredientsUseCase {
    Result handle(Query query);

    /**
     * @param type filtro opcional por tipo; nulo/em branco lista todos
     */
    record Query(UUID breweryId, String type, int page, int size) {}

    record Summary(UUID id, String type, String code, String name, String useUnit, String purchaseUnit,
            Map<String, String> attributes, boolean active, long version) {}

    record Result(List<Summary> content, int page, int size, long totalElements, int totalPages) {}
}
