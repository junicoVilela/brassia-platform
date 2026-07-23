package br.com.brew.brassia.water.application.port.inbound;

import java.util.List;
import java.util.UUID;

@FunctionalInterface
public interface ListWaterSourcesUseCase {
    Result handle(Query query);

    record Query(UUID breweryId, int page, int size) {}

    record Summary(UUID id, String code, String name, boolean active, long version) {}

    record Result(List<Summary> content, int page, int size, long totalElements, int totalPages) {}
}
