package br.com.brew.brassia.sanitation.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record ConsumptionRequest(
        @NotNull @PositiveOrZero BigDecimal waterLiters,
        @NotNull @PositiveOrZero BigDecimal energyKwh,
        @NotNull @PositiveOrZero BigDecimal productKg) {}
