package br.com.brew.brassia.quality.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Payloads de entrada da qualidade (QLT-001). */
public final class QualityDtos {

    private QualityDtos() {
    }

    public record CreatePlan(@NotBlank @Size(max = 40) String code, @NotBlank @Size(max = 120) String name,
            UUID recipeId, @NotBlank String stage) {}

    public record AmendPlan(@NotBlank @Size(max = 120) String name, UUID recipeId, @NotBlank String stage) {}

    /** Ao menos um limite; o backend recusa faixa sem piso nem teto. */
    public record AddPoint(@NotBlank @Size(max = 120) String parameter, BigDecimal min, BigDecimal max,
            BigDecimal target, @NotBlank @Size(max = 20) String unit, @NotBlank String frequencyKind,
            Integer everyHours, @NotBlank @Size(max = 500) String action, @NotBlank String severity,
            boolean critical) {}

    public record RecordMeasurement(@NotNull UUID planId, @NotNull UUID pointId, UUID batchId,
            UUID instrumentId, @NotNull BigDecimal value, @Size(max = 500) String note,
            Instant measuredAt) {}
}
