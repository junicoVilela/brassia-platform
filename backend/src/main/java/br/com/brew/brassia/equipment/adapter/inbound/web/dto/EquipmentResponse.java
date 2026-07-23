package br.com.brew.brassia.equipment.adapter.inbound.web.dto;

import br.com.brew.brassia.equipment.application.port.inbound.ListEquipmentUseCase;
import br.com.brew.brassia.equipment.application.port.inbound.RegisterEquipmentUseCase;
import br.com.brew.brassia.equipment.application.port.inbound.UpdateEquipmentUseCase;
import java.math.BigDecimal;
import java.util.UUID;

public record EquipmentResponse(
        UUID id,
        String code,
        String name,
        BigDecimal capacityLiters,
        BigDecimal deadSpaceLiters,
        BigDecimal mashEfficiencyPercent,
        BigDecimal boilOffLitersPerHour,
        boolean active,
        long version) {

    public static EquipmentResponse from(RegisterEquipmentUseCase.Result r) {
        return new EquipmentResponse(r.id(), r.code(), r.name(), r.capacityLiters(), r.deadSpaceLiters(),
                r.mashEfficiencyPercent(), r.boilOffLitersPerHour(), r.active(), r.version());
    }

    public static EquipmentResponse from(UpdateEquipmentUseCase.Result r) {
        return new EquipmentResponse(r.id(), r.code(), r.name(), r.capacityLiters(), r.deadSpaceLiters(),
                r.mashEfficiencyPercent(), r.boilOffLitersPerHour(), r.active(), r.version());
    }

    public static EquipmentResponse from(ListEquipmentUseCase.Summary s) {
        return new EquipmentResponse(s.id(), s.code(), s.name(), s.capacityLiters(), s.deadSpaceLiters(),
                s.mashEfficiencyPercent(), s.boilOffLitersPerHour(), s.active(), s.version());
    }
}
