package br.com.brew.brassia.equipment.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.equipment.application.port.inbound.CancelMaintenanceUseCase;
import br.com.brew.brassia.equipment.application.port.outbound.MaintenanceRepository;
import java.util.Map;
import java.util.Objects;

public final class CancelMaintenanceHandler implements CancelMaintenanceUseCase {
    private final MaintenanceRepository maintenance;
    private final AuditTrail audit;

    public CancelMaintenanceHandler(MaintenanceRepository maintenance, AuditTrail audit) {
        this.maintenance = Objects.requireNonNull(maintenance);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public void handle(Command command) {
        var window = maintenance.findById(command.breweryId(), command.equipmentId(), command.maintenanceId())
                .orElseThrow(() -> new IllegalArgumentException("janela inexistente"));
        var expectedVersion = window.version();
        window.cancel();
        if (!maintenance.updateStatus(window, expectedVersion)) {
            throw new IllegalStateException("não foi possível cancelar (concorrência)");
        }
        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "equipment.maintenance.cancel",
                "equipment_maintenance", command.maintenanceId().toString(),
                Map.of("equipmentId", command.equipmentId().toString())));
    }
}
