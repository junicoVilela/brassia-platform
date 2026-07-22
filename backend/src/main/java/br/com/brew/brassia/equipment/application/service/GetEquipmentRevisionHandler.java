package br.com.brew.brassia.equipment.application.service;

import br.com.brew.brassia.equipment.application.port.inbound.GetEquipmentRevisionUseCase;
import br.com.brew.brassia.equipment.application.port.outbound.EquipmentRepository;
import java.util.Objects;

public final class GetEquipmentRevisionHandler implements GetEquipmentRevisionUseCase {
    private final EquipmentRepository repository;

    public GetEquipmentRevisionHandler(EquipmentRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public Result handle(Query query) {
        var snapshot = repository.findRevision(query.breweryId(), query.equipmentId(), query.version())
                .orElseThrow(() -> new IllegalArgumentException("revisão inexistente"));
        return new Result(snapshot.equipmentId(), snapshot.code(), snapshot.name(), snapshot.capacityLiters(),
                snapshot.deadSpaceLiters(), snapshot.mashEfficiencyPercent(), snapshot.boilOffLitersPerHour(),
                snapshot.version());
    }
}
