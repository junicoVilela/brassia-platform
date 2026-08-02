package br.com.brew.brassia.packaging.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record PlanPackagingRequest(
        @NotBlank @Size(max = 40) String code,
        @NotNull UUID batchId,
        @NotNull UUID containerId,
        @Positive int plannedUnits,
        @NotNull UUID lineEquipmentId,
        @NotNull Instant plannedStart,
        @NotNull Instant plannedEnd) {}
