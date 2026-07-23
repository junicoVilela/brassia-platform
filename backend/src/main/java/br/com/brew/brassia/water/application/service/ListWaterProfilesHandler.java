package br.com.brew.brassia.water.application.service;

import br.com.brew.brassia.water.application.port.inbound.ListWaterProfilesUseCase;
import br.com.brew.brassia.water.application.port.outbound.WaterProfileRepository;
import br.com.brew.brassia.water.domain.WaterProfile;
import java.util.Objects;

public final class ListWaterProfilesHandler implements ListWaterProfilesUseCase {
    private final WaterProfileRepository repository;

    public ListWaterProfilesHandler(WaterProfileRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public Result handle(Query query) {
        var content = repository.findPage(query.breweryId(), query.page(), query.size())
                .stream().map(ListWaterProfilesHandler::toSummary).toList();
        var total = repository.count(query.breweryId());
        var totalPages = query.size() == 0 ? 0 : (int) Math.ceil((double) total / query.size());
        return new Result(content, query.page(), query.size(), total, totalPages);
    }

    private static Summary toSummary(WaterProfile p) {
        var t = p.targets();
        return new Summary(p.id().value(), p.code().value(), p.name().value(), t.calcium(), t.magnesium(),
                t.sodium(), t.sulfate(), t.chloride(), t.bicarbonate(), p.active(), p.version());
    }
}
