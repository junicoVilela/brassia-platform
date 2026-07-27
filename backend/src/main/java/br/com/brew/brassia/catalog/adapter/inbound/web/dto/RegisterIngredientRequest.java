package br.com.brew.brassia.catalog.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.Map;

public record RegisterIngredientRequest(
        @NotBlank String type,
        @NotBlank String code,
        @NotBlank String name,
        @NotBlank String useUnit,
        @NotBlank String purchaseUnit,
        @Positive BigDecimal purchasePackageSize,
        Map<String, String> attributes) {}
