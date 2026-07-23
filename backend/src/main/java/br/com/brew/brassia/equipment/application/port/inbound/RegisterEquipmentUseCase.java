package br.com.brew.brassia.equipment.application.port.inbound;

import java.math.BigDecimal;
import java.util.UUID;

public interface RegisterEquipmentUseCase {
    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, String code, String name, BigDecimal capacityLiters,
            BigDecimal deadSpaceLiters, BigDecimal mashEfficiencyPercent, BigDecimal boilOffLitersPerHour) {}

    record Result(UUID id, String code, String name, BigDecimal capacityLiters, BigDecimal deadSpaceLiters,
            BigDecimal mashEfficiencyPercent, BigDecimal boilOffLitersPerHour, boolean active, long version) {}
}
