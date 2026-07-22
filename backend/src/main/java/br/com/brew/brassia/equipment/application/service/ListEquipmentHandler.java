package br.com.brew.brassia.equipment.application.service;

import br.com.brew.brassia.equipment.application.port.inbound.ListEquipmentUseCase;
import br.com.brew.brassia.equipment.application.port.outbound.EquipmentRepository;
import br.com.brew.brassia.equipment.domain.Equipment;
import java.util.Objects;

public final class ListEquipmentHandler implements ListEquipmentUseCase {
    private final EquipmentRepository repository;

    public ListEquipmentHandler(EquipmentRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public Result handle(Query query) {
        var content = repository.findPage(query.breweryId(), query.page(), query.size())
                .stream().map(ListEquipmentHandler::toSummary).toList();
        var total = repository.count(query.breweryId());
        var totalPages = query.size() == 0 ? 0 : (int) Math.ceil((double) total / query.size());
        return new Result(content, query.page(), query.size(), total, totalPages);
    }

    private static Summary toSummary(Equipment e) {
        return new Summary(e.id().value(), e.code().value(), e.name().value(), e.capacityLiters(),
                e.deadSpaceLiters(), e.mashEfficiencyPercent(), e.boilOffLitersPerHour(), e.active(), e.version());
    }
}
