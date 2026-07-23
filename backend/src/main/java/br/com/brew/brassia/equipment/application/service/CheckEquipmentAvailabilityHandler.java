package br.com.brew.brassia.equipment.application.service;

import br.com.brew.brassia.equipment.application.port.inbound.CheckEquipmentAvailabilityUseCase;
import br.com.brew.brassia.equipment.application.port.outbound.MaintenanceRepository;
import java.util.Objects;

public final class CheckEquipmentAvailabilityHandler implements CheckEquipmentAvailabilityUseCase {
    private final MaintenanceRepository maintenance;

    public CheckEquipmentAvailabilityHandler(MaintenanceRepository maintenance) {
        this.maintenance = Objects.requireNonNull(maintenance);
    }

    @Override
    public Result handle(Query query) {
        if (query.from() == null || query.to() == null || !query.to().isAfter(query.from())) {
            throw new IllegalArgumentException("intervalo inválido");
        }
        var available = !maintenance.hasScheduledOverlap(
                query.breweryId(), query.equipmentId(), query.from(), query.to());
        return new Result(query.equipmentId(), query.from(), query.to(), available);
    }
}
