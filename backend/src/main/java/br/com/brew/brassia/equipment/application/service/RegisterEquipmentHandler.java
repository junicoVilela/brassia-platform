package br.com.brew.brassia.equipment.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.equipment.application.port.inbound.RegisterEquipmentUseCase;
import br.com.brew.brassia.equipment.application.port.outbound.EquipmentRepository;
import br.com.brew.brassia.equipment.domain.Equipment;
import br.com.brew.brassia.equipment.domain.EquipmentCode;
import br.com.brew.brassia.equipment.domain.EquipmentName;
import java.util.Map;
import java.util.Objects;

public final class RegisterEquipmentHandler implements RegisterEquipmentUseCase {
    private final EquipmentRepository repository;
    private final AuditTrail audit;

    public RegisterEquipmentHandler(EquipmentRepository repository, AuditTrail audit) {
        this.repository = Objects.requireNonNull(repository);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var code = new EquipmentCode(command.code());
        var name = new EquipmentName(command.name());

        if (repository.existsByCode(command.breweryId(), code.value())) {
            throw new IllegalStateException("código de equipamento já existe nesta cervejaria");
        }

        var equipment = Equipment.register(command.breweryId(), code, name, command.capacityLiters(),
                command.deadSpaceLiters(), command.mashEfficiencyPercent(), command.boilOffLitersPerHour());
        repository.insert(equipment);
        repository.appendRevision(equipment.snapshot(), command.actorId());

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "equipment.register",
                "equipment", equipment.id().value().toString(),
                Map.of("code", code.value(), "version", Long.toString(equipment.version()))));

        return new Result(equipment.id().value(), code.value(), name.value(), equipment.capacityLiters(),
                equipment.deadSpaceLiters(), equipment.mashEfficiencyPercent(), equipment.boilOffLitersPerHour(),
                equipment.active(), equipment.version());
    }
}
