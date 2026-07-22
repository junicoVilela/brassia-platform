package br.com.brew.brassia.brewery.adapter.inbound.web.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PreferencesResponse(
        UUID breweryId,
        String volumeUnit,
        String massUnit,
        String temperatureUnit,
        String currencyCode,
        BigDecimal maxBatchVolume,
        boolean allowNegativeStock,
        String stockPolicy,
        long version) {}
