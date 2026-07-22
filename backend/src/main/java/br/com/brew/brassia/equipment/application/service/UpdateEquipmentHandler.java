package br.com.brew.brassia.equipment.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.equipment.application.port.inbound.UpdateEquipmentUseCase;
import br.com.brew.brassia.equipment.application.port.outbound.EquipmentRepository;
import br.com.brew.brassia.equipment.domain.EquipmentName;
import java.util.Map;
import java.util.Objects;

public final class UpdateEquipmentHandler implements UpdateEquipmentUseCase {
    private final EquipmentRepository repository;
    private final AuditTrail audit;

    public UpdateEquipmentHandler(EquipmentRepository repository, AuditTrail audit) {
        this.repository = Objects.requireNonNull(repository);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var equipment = repository.findById(command.breweryId(), command.equipmentId())
                .orElseThrow(() -> new IllegalArgumentException("equipamento inexistente"));

        equipment.update(new EquipmentName(command.name()), command.capacityLiters(), command.deadSpaceLiters(),
                command.mashEfficiencyPercent(), command.boilOffLitersPerHour());

        if (!repository.update(equipment, command.version())) {
            throw new IllegalStateException("versão do equipamento divergiu");
        }

        var refreshed = repository.findById(command.breweryId(), command.equipmentId()).orElseThrow();
        repository.appendRevision(refreshed.snapshot(), command.actorId());
        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "equipment.update",
                "equipment", refreshed.id().value().toString(),
                Map.of("code", refreshed.code().value(), "version", Long.toString(refreshed.version()))));

        return new Result(refreshed.id().value(), refreshed.code().value(), refreshed.name().value(),
                refreshed.capacityLiters(), refreshed.deadSpaceLiters(), refreshed.mashEfficiencyPercent(),
                refreshed.boilOffLitersPerHour(), refreshed.active(), refreshed.version());
    }
}
