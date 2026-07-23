package br.com.brew.brassia.equipment.application.port.inbound;

import java.math.BigDecimal;
import java.util.UUID;

@FunctionalInterface
public interface GetEquipmentRevisionUseCase {
    Result handle(Query query);

    record Query(UUID breweryId, UUID equipmentId, long version) {}

    record Result(UUID id, String code, String name, BigDecimal capacityLiters, BigDecimal deadSpaceLiters,
            BigDecimal mashEfficiencyPercent, BigDecimal boilOffLitersPerHour, long version) {}
}
