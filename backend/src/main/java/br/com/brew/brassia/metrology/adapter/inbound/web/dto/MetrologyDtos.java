package br.com.brew.brassia.metrology.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Payloads de entrada da metrologia (MTR-001). */
public final class MetrologyDtos {

    private MetrologyDtos() {
    }

    public record RegisterInstrument(@NotBlank @Size(max = 40) String code, @NotBlank @Size(max = 120) String name,
            @NotBlank String type, @NotNull BigDecimal rangeMin, @NotNull BigDecimal rangeMax,
            @NotNull BigDecimal resolution, @NotNull BigDecimal accuracy, @NotBlank @Size(max = 20) String unit,
            @NotBlank @Size(max = 120) String location) {}

    public record AmendInstrument(@NotBlank @Size(max = 120) String name, @NotNull BigDecimal rangeMin,
            @NotNull BigDecimal rangeMax, @NotNull BigDecimal resolution, @NotNull BigDecimal accuracy,
            @NotBlank @Size(max = 20) String unit, @NotBlank @Size(max = 120) String location) {}

    public record BlockInstrument(@Size(max = 200) String reason) {}

    public record RetireInstrument(@NotBlank @Size(max = 200) String reason) {}

    public record DesignateCriticalUse(@NotNull Boolean criticalUse) {}

    public record RecordCalibration(@NotNull UUID standardId, @NotNull LocalDate performedOn,
            @NotNull LocalDate dueOn, @NotBlank @Size(max = 120) String performedBy,
            @NotBlank @Size(max = 60) String certificateNumber, @NotBlank String result,
            @NotNull BigDecimal maxDeviation, @Size(max = 200) String restriction,
            @Size(max = 500) String note) {}

    public record RegisterStandard(@NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 200) String description, @NotBlank @Size(max = 60) String certificateNumber,
            @NotBlank @Size(max = 120) String issuer, @NotBlank @Size(max = 120) String traceability,
            @NotNull LocalDate validUntil) {}

    public record RenewStandard(@NotBlank @Size(max = 60) String certificateNumber,
            @NotBlank @Size(max = 120) String issuer, @NotNull LocalDate validUntil,
            @NotNull LocalDate issuedOn) {}
}
