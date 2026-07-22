package br.com.brew.brassia.brewery.application.port.inbound;

import java.math.BigDecimal;
import java.util.UUID;

public interface UpdateOperationalPreferencesUseCase {
    Result handle(Command command);

    record Command(
            UUID actorId,
            UUID breweryId,
            String volumeUnit,
            String massUnit,
            String temperatureUnit,
            String currencyCode,
            BigDecimal maxBatchVolume,
            boolean allowNegativeStock,
            String stockPolicy,
            long version) {}

    record Result(
            UUID breweryId,
            String volumeUnit,
            String massUnit,
            String temperatureUnit,
            String currencyCode,
            BigDecimal maxBatchVolume,
            boolean allowNegativeStock,
            String stockPolicy,
            long version) {}
}
