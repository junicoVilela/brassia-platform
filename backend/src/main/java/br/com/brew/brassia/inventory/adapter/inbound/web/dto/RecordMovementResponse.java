package br.com.brew.brassia.inventory.adapter.inbound.web.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record RecordMovementResponse(UUID movementId, BigDecimal onHand, BigDecimal available) {}
