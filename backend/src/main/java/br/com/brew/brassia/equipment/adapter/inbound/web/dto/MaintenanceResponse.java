package br.com.brew.brassia.equipment.adapter.inbound.web.dto;

import br.com.brew.brassia.equipment.application.port.inbound.ListEquipmentMaintenanceUseCase;
import br.com.brew.brassia.equipment.application.port.inbound.ScheduleMaintenanceUseCase;
import java.time.Instant;
import java.util.UUID;

public record MaintenanceResponse(
        UUID id,
        UUID equipmentId,
        String kind,
        String instrument,
        Instant startAt,
        Instant endAt,
        String notes,
        String status,
        long version) {

    public static MaintenanceResponse from(ScheduleMaintenanceUseCase.Result r) {
        return new MaintenanceResponse(r.id(), r.equipmentId(), r.kind(), r.instrument(), r.startAt(), r.endAt(),
                r.notes(), r.status(), r.version());
    }

    public static MaintenanceResponse from(ListEquipmentMaintenanceUseCase.Window w) {
        return new MaintenanceResponse(w.id(), w.equipmentId(), w.kind(), w.instrument(), w.startAt(), w.endAt(),
                w.notes(), w.status(), w.version());
    }
}
