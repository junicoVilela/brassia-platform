package br.com.brew.brassia.planning.application.service;

import br.com.brew.brassia.planning.application.port.inbound.ListBrewOrdersUseCase;
import br.com.brew.brassia.planning.application.port.outbound.BrewOrderRepository;
import java.util.Objects;

public final class ListBrewOrdersHandler implements ListBrewOrdersUseCase {

    private final BrewOrderRepository repository;

    public ListBrewOrdersHandler(BrewOrderRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public Result handle(Query query) {
        int page = Math.max(0, query.page());
        int size = query.size() <= 0 ? 20 : Math.min(query.size(), 100);
        var content = repository.findPage(query.breweryId(), page, size);
        long total = repository.count(query.breweryId());
        int totalPages = (int) Math.ceil((double) total / size);
        return new Result(content, page, size, total, totalPages);
    }
}
