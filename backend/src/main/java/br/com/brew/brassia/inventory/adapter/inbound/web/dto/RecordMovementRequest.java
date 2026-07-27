package br.com.brew.brassia.inventory.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record RecordMovementRequest(
        @NotBlank String type,
        @NotNull @Positive BigDecimal quantity,
        String reason,
        Boolean allowNegative) {}
