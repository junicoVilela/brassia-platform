package br.com.brew.brassia.brewery.application.port.inbound;

import java.math.BigDecimal;
import java.util.UUID;

public interface GetOperationalPreferencesUseCase {
    Result handle(Query query);

    record Query(UUID breweryId) {}

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
