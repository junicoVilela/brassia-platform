package br.com.brew.brassia.equipment.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record UpdateEquipmentRequest(
        @NotBlank String name,
        @NotNull BigDecimal capacityLiters,
        @NotNull BigDecimal deadSpaceLiters,
        @NotNull BigDecimal mashEfficiencyPercent,
        @NotNull BigDecimal boilOffLitersPerHour,
        long version) {}
