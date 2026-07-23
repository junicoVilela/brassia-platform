package br.com.brew.brassia.water.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record SimulateBlendRequest(
        @NotEmpty List<Input> inputs,
        UUID targetProfileId) {

    public record Input(@NotNull UUID sourceId, @NotNull BigDecimal volumeLiters) {}
}
