package br.com.brew.brassia.water.application.port.inbound;

import java.math.BigDecimal;
import java.util.UUID;

public interface RegisterWaterProfileUseCase {
    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, String code, String name, BigDecimal calcium,
            BigDecimal magnesium, BigDecimal sodium, BigDecimal sulfate, BigDecimal chloride,
            BigDecimal bicarbonate) {}

    record Result(UUID id, String code, String name, BigDecimal calcium, BigDecimal magnesium,
            BigDecimal sodium, BigDecimal sulfate, BigDecimal chloride, BigDecimal bicarbonate,
            boolean active, long version) {}
}
