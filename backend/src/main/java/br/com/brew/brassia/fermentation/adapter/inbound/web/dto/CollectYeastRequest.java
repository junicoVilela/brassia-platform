package br.com.brew.brassia.fermentation.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** {@code parentHarvestId} nulo = levedura comprada; a geração é derivada, nunca informada. */
public record CollectYeastRequest(
        @NotBlank String code,
        @NotNull UUID strainId,
        @NotNull UUID sourceBatchId,
        UUID parentHarvestId,
        @NotNull Instant harvestedAt,
        @NotNull BigDecimal viabilityPercent,
        @NotBlank String condition,
        @NotBlank String storageLocation,
        @NotNull BigDecimal storageTempC) {}
