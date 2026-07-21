package br.com.brew.brassia.brewery.application.service;

import br.com.brew.brassia.brewery.application.port.inbound.ListBreweriesUseCase;
import br.com.brew.brassia.brewery.application.port.outbound.BreweryRepository;
import br.com.brew.brassia.brewery.domain.Brewery;
import java.util.Objects;

public final class ListBreweriesHandler implements ListBreweriesUseCase {
    private static final int MAX_SIZE = 100;

    private final BreweryRepository repository;

    public ListBreweriesHandler(BreweryRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public Result handle(Query query) {
        var page = Math.max(0, query.page());
        var size = Math.clamp(query.size(), 1, MAX_SIZE);

        var content = repository.findPage(page, size).stream().map(ListBreweriesHandler::toSummary).toList();
        var total = repository.count();
        var totalPages = (int) Math.ceilDiv(total, size);

        return new Result(content, page, size, total, totalPages);
    }

    private static Summary toSummary(Brewery brewery) {
        return new Summary(brewery.id().value(), brewery.code().value(),
                brewery.name().value(), brewery.timezone().value());
    }
}
