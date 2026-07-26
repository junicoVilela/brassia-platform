package br.com.brew.brassia.inventory.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

public record ReserveStockRequest(
        @NotNull UUID ingredientId,
        @NotNull @Positive BigDecimal quantity,
        @NotBlank String unit,
        UUID orderId) {}
