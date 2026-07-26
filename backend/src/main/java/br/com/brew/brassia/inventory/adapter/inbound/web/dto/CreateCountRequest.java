package br.com.brew.brassia.inventory.adapter.inbound.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateCountRequest(@NotEmpty @Valid List<Line> lines) {

    public record Line(@NotNull UUID lotId, @NotNull @PositiveOrZero BigDecimal countedQuantity) {}
}
