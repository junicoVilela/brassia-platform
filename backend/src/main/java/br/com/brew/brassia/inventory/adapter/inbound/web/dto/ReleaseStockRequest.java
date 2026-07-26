package br.com.brew.brassia.inventory.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ReleaseStockRequest(@NotNull UUID orderId) {}
