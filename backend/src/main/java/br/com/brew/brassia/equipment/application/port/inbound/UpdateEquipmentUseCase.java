package br.com.brew.brassia.equipment.application.port.inbound;

import java.math.BigDecimal;
import java.util.UUID;

public interface UpdateEquipmentUseCase {
    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID equipmentId, String name, BigDecimal capacityLiters,
            BigDecimal deadSpaceLiters, BigDecimal mashEfficiencyPercent, BigDecimal boilOffLitersPerHour,
            long version) {}

    record Result(UUID id, String code, String name, BigDecimal capacityLiters, BigDecimal deadSpaceLiters,
            BigDecimal mashEfficiencyPercent, BigDecimal boilOffLitersPerHour, boolean active, long version) {}
}
