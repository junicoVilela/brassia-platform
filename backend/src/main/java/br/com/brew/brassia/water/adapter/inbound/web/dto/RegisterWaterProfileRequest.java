package br.com.brew.brassia.water.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record RegisterWaterProfileRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull BigDecimal calcium,
        @NotNull BigDecimal magnesium,
        @NotNull BigDecimal sodium,
        @NotNull BigDecimal sulfate,
        @NotNull BigDecimal chloride,
        @NotNull BigDecimal bicarbonate) {}
