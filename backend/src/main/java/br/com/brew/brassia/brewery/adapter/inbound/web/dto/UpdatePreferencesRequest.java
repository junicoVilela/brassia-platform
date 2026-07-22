package br.com.brew.brassia.brewery.adapter.inbound.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdatePreferencesRequest(
        @NotBlank @Size(max = 8) String volumeUnit,
        @NotBlank @Size(max = 8) String massUnit,
        @NotBlank @Size(max = 8) String temperatureUnit,
        @NotBlank @Size(min = 3, max = 3) String currencyCode,
        @NotNull @DecimalMin("0.000001") BigDecimal maxBatchVolume,
        boolean allowNegativeStock,
        @NotBlank @Size(max = 16) String stockPolicy,
        long version) {}
