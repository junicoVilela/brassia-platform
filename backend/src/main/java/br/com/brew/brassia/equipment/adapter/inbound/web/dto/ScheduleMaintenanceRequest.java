package br.com.brew.brassia.equipment.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record ScheduleMaintenanceRequest(
        @NotBlank String kind,
        String instrument,
        @NotNull Instant startAt,
        @NotNull Instant endAt,
        String notes) {}
