package br.com.brew.brassia.water.application.port.inbound;

import java.math.BigDecimal;
import java.util.UUID;

public interface UpdateWaterProfileUseCase {
    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID profileId, String name, BigDecimal calcium,
            BigDecimal magnesium, BigDecimal sodium, BigDecimal sulfate, BigDecimal chloride,
            BigDecimal bicarbonate, long version) {}

    record Result(UUID id, String code, String name, BigDecimal calcium, BigDecimal magnesium,
            BigDecimal sodium, BigDecimal sulfate, BigDecimal chloride, BigDecimal bicarbonate,
            boolean active, long version) {}
}
