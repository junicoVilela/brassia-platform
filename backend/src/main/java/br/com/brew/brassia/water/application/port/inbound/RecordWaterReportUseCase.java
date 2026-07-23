package br.com.brew.brassia.water.application.port.inbound;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public interface RecordWaterReportUseCase {
    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID sourceId, LocalDate collectedOn, String method,
            BigDecimal calcium, BigDecimal magnesium, BigDecimal sodium, BigDecimal sulfate,
            BigDecimal chloride, BigDecimal bicarbonate, String notes) {}

    record Result(UUID id, UUID sourceId, LocalDate collectedOn, String method, BigDecimal calcium,
            BigDecimal magnesium, BigDecimal sodium, BigDecimal sulfate, BigDecimal chloride,
            BigDecimal bicarbonate, String notes) {}
}
