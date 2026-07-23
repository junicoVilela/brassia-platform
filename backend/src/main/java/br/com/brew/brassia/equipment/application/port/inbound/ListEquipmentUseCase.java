package br.com.brew.brassia.equipment.application.port.inbound;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@FunctionalInterface
public interface ListEquipmentUseCase {
    Result handle(Query query);

    record Query(UUID breweryId, int page, int size) {}

    record Summary(UUID id, String code, String name, BigDecimal capacityLiters, BigDecimal deadSpaceLiters,
            BigDecimal mashEfficiencyPercent, BigDecimal boilOffLitersPerHour, boolean active, long version) {}

    record Result(List<Summary> content, int page, int size, long totalElements, int totalPages) {}
}
