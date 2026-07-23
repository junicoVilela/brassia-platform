package br.com.brew.brassia.water.application.port.inbound;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@FunctionalInterface
public interface ListWaterReportsUseCase {
    List<Report> handle(Query query);

    record Query(UUID breweryId, UUID sourceId) {}

    record Report(UUID id, UUID sourceId, LocalDate collectedOn, String method, BigDecimal calcium,
            BigDecimal magnesium, BigDecimal sodium, BigDecimal sulfate, BigDecimal chloride,
            BigDecimal bicarbonate, String notes) {}
}
