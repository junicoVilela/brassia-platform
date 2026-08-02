package br.com.brew.brassia.gas.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Contratos de entrada da API de gases (GAS-001). */
public final class GasDtos {

    private GasDtos() {
    }

    public record RegisterCylinderRequest(
            @NotBlank @Size(max = 40) String code,
            @NotBlank String gasType,
            @NotNull @Positive BigDecimal capacityKg,
            @NotNull @Positive BigDecimal tareKg,
            @NotNull @PositiveOrZero BigDecimal contentKg,
            @NotNull LocalDate requalificationDueOn,
            @NotBlank @Size(max = 120) String location) {}

    /** {@code reason} é obrigatório para bloquear; ignorado no desbloqueio. */
    public record BlockCylinderRequest(boolean blocked, @Size(max = 200) String reason) {}

    public record RequalifyRequest(@NotNull LocalDate dueOn) {}

    public record RefillRequest(@NotNull @PositiveOrZero BigDecimal contentKg) {}

    public record RegisterComponentRequest(
            @NotBlank String kind,
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 120) String name,
            @NotNull @Positive BigDecimal maxPressureBar,
            BigDecimal setPressureBar) {}

    public record UpdateComponentRequest(
            @NotBlank @Size(max = 120) String name,
            @NotNull @Positive BigDecimal maxPressureBar,
            BigDecimal setPressureBar) {}

    public record SetActiveRequest(boolean active) {}

    public record ConnectRequest(
            @NotNull UUID cylinderId,
            @NotNull UUID regulatorId,
            UUID manifoldId,
            @NotNull UUID pointOfUseEquipmentId,
            @NotNull @Positive BigDecimal workingPressureBar) {}

    public record LeakTestRequest(
            boolean passed,
            @NotBlank @Size(max = 120) String method,
            @NotNull @PositiveOrZero BigDecimal pressureDropBar,
            @Size(max = 200) String note) {}

    public record PressureRequest(@NotNull @Positive BigDecimal bar, BigDecimal tempC) {}

    public record ConsumptionRequest(@NotNull @Positive BigDecimal kg, @Size(max = 200) String reason) {}

    public record DisconnectRequest(@NotBlank @Size(max = 200) String reason) {}
}
