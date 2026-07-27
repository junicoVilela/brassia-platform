package br.com.brew.brassia.production.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record RecordMeasurementRequest(
        UUID stepId,
        @NotBlank String kind,
        @NotNull BigDecimal value,
        @NotBlank String unit,
        BigDecimal temperatureC,
        String method,
        @NotBlank String source) {}
