package br.com.brew.brassia.fermentation.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RecordReadingRequest(
        @NotNull UUID batchId,
        @NotBlank String kind,
        @NotBlank String source,
        @NotNull BigDecimal value,
        @NotBlank String unit,
        @NotNull Instant measuredAt) {}
