package br.com.brew.brassia.production.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequest(
        @NotNull UUID destinationEquipmentId,
        @NotNull @Positive BigDecimal volumeLiters,
        @NotNull @Positive BigDecimal ogSg,
        @PositiveOrZero BigDecimal lossesLiters) {}
