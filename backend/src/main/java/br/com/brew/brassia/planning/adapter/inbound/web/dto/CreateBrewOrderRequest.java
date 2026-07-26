package br.com.brew.brassia.planning.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateBrewOrderRequest(
        @NotNull UUID recipeId,
        @NotNull @Positive BigDecimal volumeLiters) {}
