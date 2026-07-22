package br.com.brew.brassia.catalog.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record UpdateIngredientRequest(
        @NotBlank String name,
        @NotBlank String useUnit,
        @NotBlank String purchaseUnit,
        Map<String, String> attributes,
        long version) {}
