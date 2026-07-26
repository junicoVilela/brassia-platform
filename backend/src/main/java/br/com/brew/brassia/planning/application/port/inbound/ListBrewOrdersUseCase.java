package br.com.brew.brassia.planning.application.port.inbound;

import br.com.brew.brassia.planning.domain.BrewOrder;
import java.util.List;
import java.util.UUID;

public interface ListBrewOrdersUseCase {
    Result handle(Query query);

    record Query(UUID breweryId, int page, int size) {}

    record Result(List<BrewOrder> content, int page, int size, long totalElements, int totalPages) {}
}
