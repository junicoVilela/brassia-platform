package br.com.brew.brassia.recipe.application.port.inbound;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@FunctionalInterface
public interface ListRecipesUseCase {
    Result handle(Query query);

    record Query(UUID breweryId, int page, int size) {}

    record Summary(UUID id, String name, String status, BigDecimal batchVolumeLiters, long version) {}

    record Result(List<Summary> content, int page, int size, long totalElements, int totalPages) {}
}
