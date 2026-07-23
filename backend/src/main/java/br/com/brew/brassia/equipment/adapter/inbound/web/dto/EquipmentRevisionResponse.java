package br.com.brew.brassia.equipment.adapter.inbound.web.dto;

import br.com.brew.brassia.equipment.application.port.inbound.GetEquipmentRevisionUseCase;
import java.math.BigDecimal;
import java.util.UUID;

public record EquipmentRevisionResponse(
        UUID id,
        String code,
        String name,
        BigDecimal capacityLiters,
        BigDecimal deadSpaceLiters,
        BigDecimal mashEfficiencyPercent,
        BigDecimal boilOffLitersPerHour,
        long version) {

    public static EquipmentRevisionResponse from(GetEquipmentRevisionUseCase.Result r) {
        return new EquipmentRevisionResponse(r.id(), r.code(), r.name(), r.capacityLiters(), r.deadSpaceLiters(),
                r.mashEfficiencyPercent(), r.boilOffLitersPerHour(), r.version());
    }
}
