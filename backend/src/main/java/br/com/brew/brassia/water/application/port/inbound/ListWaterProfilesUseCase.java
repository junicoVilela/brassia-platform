package br.com.brew.brassia.water.application.port.inbound;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@FunctionalInterface
public interface ListWaterProfilesUseCase {
    Result handle(Query query);

    record Query(UUID breweryId, int page, int size) {}

    record Summary(UUID id, String code, String name, BigDecimal calcium, BigDecimal magnesium,
            BigDecimal sodium, BigDecimal sulfate, BigDecimal chloride, BigDecimal bicarbonate,
            boolean active, long version) {}

    record Result(List<Summary> content, int page, int size, long totalElements, int totalPages) {}
}
