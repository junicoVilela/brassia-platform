package br.com.brew.brassia.brewery.application.port.inbound;

import java.util.List;
import java.util.UUID;

@FunctionalInterface
public interface ListBreweriesUseCase {
    Result handle(Query query);

    record Query(int page, int size) {}

    record Summary(UUID id, String code, String name, String timezone) {}

    record Result(List<Summary> content, int page, int size, long totalElements, int totalPages) {}
}
