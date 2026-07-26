package br.com.brew.brassia.planning.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.UUID;

public record MaterialRequirementRequest(
        @NotNull UUID recipeId,
        @NotNull @Positive BigDecimal volumeLiters,
        @PositiveOrZero BigDecimal lossPercent) {}
