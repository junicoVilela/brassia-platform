package br.com.brew.brassia.inventory.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ReceiveStockLotRequest(
        @NotNull UUID ingredientId,
        @NotNull UUID supplierId,
        String supplierLotCode,
        @NotNull @Positive BigDecimal quantity,
        @NotBlank String unit,
        @NotNull @PositiveOrZero BigDecimal unitCost,
        LocalDate expiryDate,
        @NotBlank String inspection) {}
