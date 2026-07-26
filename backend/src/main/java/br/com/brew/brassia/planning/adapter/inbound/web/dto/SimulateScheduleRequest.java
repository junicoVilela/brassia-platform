package br.com.brew.brassia.planning.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record SimulateScheduleRequest(
        @NotNull UUID equipmentId,
        @NotNull Instant scheduledStart,
        @NotNull Instant scheduledEnd) {}
