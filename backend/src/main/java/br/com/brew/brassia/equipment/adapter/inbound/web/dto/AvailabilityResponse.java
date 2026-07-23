package br.com.brew.brassia.equipment.adapter.inbound.web.dto;

import br.com.brew.brassia.equipment.application.port.inbound.CheckEquipmentAvailabilityUseCase;
import java.time.Instant;
import java.util.UUID;

public record AvailabilityResponse(UUID equipmentId, Instant from, Instant to, boolean available) {

    public static AvailabilityResponse from(CheckEquipmentAvailabilityUseCase.Result r) {
        return new AvailabilityResponse(r.equipmentId(), r.from(), r.to(), r.available());
    }
}
