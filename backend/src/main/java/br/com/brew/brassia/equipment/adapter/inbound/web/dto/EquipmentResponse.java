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
        long version,
        String cleanliness,
        java.time.Instant soiledSince) {

    /** Recém-cadastrado nasce limpo: nunca sujou, ninguém precisou limpar. */
    public static EquipmentResponse from(RegisterEquipmentUseCase.Result r) {
        return new EquipmentResponse(r.id(), r.code(), r.name(), r.capacityLiters(), r.deadSpaceLiters(),
                r.mashEfficiencyPercent(), r.boilOffLitersPerHour(), r.active(), r.version(), "CLEAN", null);
    }

    /** Editar o perfil não mexe no estado de limpeza; a resposta da edição não afirma nada sobre ele. */
    public static EquipmentResponse from(UpdateEquipmentUseCase.Result r) {
        return new EquipmentResponse(r.id(), r.code(), r.name(), r.capacityLiters(), r.deadSpaceLiters(),
                r.mashEfficiencyPercent(), r.boilOffLitersPerHour(), r.active(), r.version(), null, null);
    }

    public static EquipmentResponse from(ListEquipmentUseCase.Summary s) {
        return new EquipmentResponse(s.id(), s.code(), s.name(), s.capacityLiters(), s.deadSpaceLiters(),
                s.mashEfficiencyPercent(), s.boilOffLitersPerHour(), s.active(), s.version(),
                s.cleanliness(), s.soiledSince());
    }
}
