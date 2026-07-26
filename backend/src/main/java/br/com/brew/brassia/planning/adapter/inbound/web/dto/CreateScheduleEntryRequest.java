package br.com.brew.brassia.planning.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreateScheduleEntryRequest(
        @NotNull UUID recipeId,
        @NotNull UUID equipmentId,
        @NotNull UUID assignedUserId,
        @NotNull @Positive BigDecimal plannedVolumeLiters,
        @NotNull Instant scheduledStart,
        @NotNull Instant scheduledEnd) {}
