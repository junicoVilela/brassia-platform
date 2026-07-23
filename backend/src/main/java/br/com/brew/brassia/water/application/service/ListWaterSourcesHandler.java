package br.com.brew.brassia.water.application.service;

import br.com.brew.brassia.water.application.port.inbound.ListWaterSourcesUseCase;
import br.com.brew.brassia.water.application.port.outbound.WaterSourceRepository;
import br.com.brew.brassia.water.domain.WaterSource;
import java.util.Objects;

public final class ListWaterSourcesHandler implements ListWaterSourcesUseCase {
    private final WaterSourceRepository repository;

    public ListWaterSourcesHandler(WaterSourceRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public Result handle(Query query) {
        var content = repository.findPage(query.breweryId(), query.page(), query.size())
                .stream().map(ListWaterSourcesHandler::toSummary).toList();
        var total = repository.count(query.breweryId());
        var totalPages = query.size() == 0 ? 0 : (int) Math.ceil((double) total / query.size());
        return new Result(content, query.page(), query.size(), total, totalPages);
    }

    private static Summary toSummary(WaterSource s) {
        return new Summary(s.id().value(), s.code().value(), s.name().value(), s.active(), s.version());
    }
}
