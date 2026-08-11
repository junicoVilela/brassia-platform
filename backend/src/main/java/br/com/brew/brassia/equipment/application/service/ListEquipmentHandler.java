package br.com.brew.brassia.equipment.application.service;

import br.com.brew.brassia.equipment.application.port.inbound.ListEquipmentUseCase;
import br.com.brew.brassia.equipment.application.port.outbound.CleanlinessRepository;
import br.com.brew.brassia.equipment.application.port.outbound.EquipmentRepository;
import br.com.brew.brassia.equipment.domain.Cleanliness;
import br.com.brew.brassia.equipment.domain.Equipment;
import java.util.Objects;

public final class ListEquipmentHandler implements ListEquipmentUseCase {
    private final EquipmentRepository repository;
    private final CleanlinessRepository cleanliness;

    public ListEquipmentHandler(EquipmentRepository repository, CleanlinessRepository cleanliness) {
        this.repository = Objects.requireNonNull(repository);
        this.cleanliness = Objects.requireNonNull(cleanliness);
    }

    @Override
    public Result handle(Query query) {
        var page = repository.findPage(query.breweryId(), query.page(), query.size());
        // Uma consulta para a página inteira, não uma por item: a lição da REL-002 é que o N+1 fica
        // invisível justamente quando mora no mapeador.
        var states = cleanliness.findAll(query.breweryId(),
                page.stream().map(e -> e.id().value()).toList());
        var content = page.stream().map(e -> toSummary(e, states.get(e.id().value()))).toList();
        var total = repository.count(query.breweryId());
        var totalPages = query.size() == 0 ? 0 : (int) Math.ceil((double) total / query.size());
        return new Result(content, query.page(), query.size(), total, totalPages);
    }

    private static Summary toSummary(Equipment e, Cleanliness state) {
        var current = state == null ? Cleanliness.neverUsed() : state;
        return new Summary(e.id().value(), e.code().value(), e.name().value(), e.capacityLiters(),
                e.deadSpaceLiters(), e.mashEfficiencyPercent(), e.boilOffLitersPerHour(), e.active(), e.version(),
                current.state().name(), current.soiledAt());
    }
}
