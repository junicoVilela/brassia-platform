package br.com.brew.brassia.equipment.application.service;

import br.com.brew.brassia.equipment.application.port.inbound.ListEquipmentMaintenanceUseCase;
import br.com.brew.brassia.equipment.application.port.outbound.MaintenanceRepository;
import br.com.brew.brassia.equipment.domain.EquipmentMaintenance;
import java.util.List;
import java.util.Objects;

public final class ListEquipmentMaintenanceHandler implements ListEquipmentMaintenanceUseCase {
    private final MaintenanceRepository maintenance;

    public ListEquipmentMaintenanceHandler(MaintenanceRepository maintenance) {
        this.maintenance = Objects.requireNonNull(maintenance);
    }

    @Override
    public List<Window> handle(Query query) {
        return maintenance.findByEquipment(query.breweryId(), query.equipmentId())
                .stream().map(ListEquipmentMaintenanceHandler::toWindow).toList();
    }

    private static Window toWindow(EquipmentMaintenance m) {
        return new Window(m.id().value(), m.equipmentId(), m.kind().name(), m.instrument(),
                m.range().startAt(), m.range().endAt(), m.notes(), m.status().name(), m.version());
    }
}
