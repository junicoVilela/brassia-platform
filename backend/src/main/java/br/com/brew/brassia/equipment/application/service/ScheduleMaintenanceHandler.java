package br.com.brew.brassia.equipment.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.equipment.application.port.inbound.ScheduleMaintenanceUseCase;
import br.com.brew.brassia.equipment.application.port.outbound.EquipmentRepository;
import br.com.brew.brassia.equipment.application.port.outbound.MaintenanceRepository;
import br.com.brew.brassia.equipment.domain.EquipmentMaintenance;
import br.com.brew.brassia.equipment.domain.MaintenanceKind;
import br.com.brew.brassia.equipment.domain.TimeRange;
import java.util.Map;
import java.util.Objects;

public final class ScheduleMaintenanceHandler implements ScheduleMaintenanceUseCase {
    private final EquipmentRepository equipment;
    private final MaintenanceRepository maintenance;
    private final AuditTrail audit;

    public ScheduleMaintenanceHandler(EquipmentRepository equipment, MaintenanceRepository maintenance,
            AuditTrail audit) {
        this.equipment = Objects.requireNonNull(equipment);
        this.maintenance = Objects.requireNonNull(maintenance);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        if (equipment.findById(command.breweryId(), command.equipmentId()).isEmpty()) {
            throw new IllegalArgumentException("equipamento inexistente");
        }
        var kind = MaintenanceKind.of(command.kind());
        var range = new TimeRange(command.startAt(), command.endAt());

        // Equipamento indisponível (janela já agendada no intervalo) não pode ser reservado.
        if (maintenance.hasScheduledOverlap(command.breweryId(), command.equipmentId(),
                range.startAt(), range.endAt())) {
            throw new IllegalStateException("equipamento indisponível na janela solicitada");
        }

        var window = EquipmentMaintenance.schedule(command.breweryId(), command.equipmentId(), kind,
                command.instrument(), range, command.notes());
        maintenance.insert(window);

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "equipment.maintenance.schedule",
                "equipment_maintenance", window.id().value().toString(),
                Map.of("equipmentId", command.equipmentId().toString(), "kind", kind.name())));

        return new Result(window.id().value(), window.equipmentId(), kind.name(), window.instrument(),
                range.startAt(), range.endAt(), window.notes(), window.status().name(), window.version());
    }
}
