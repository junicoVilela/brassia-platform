package br.com.brew.brassia.planning;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Evento de domínio: a produção de uma OP foi iniciada (PRD-001). Consumível por
 * outros módulos — a produção cria o lote (Batch) e o roteiro a partir dele.
 */
public record BrewOrderStarted(UUID breweryId, UUID orderId, String code, UUID recipeId, int recipeVersion,
        String recipeName, BigDecimal volumeLiters, UUID actorId, Instant occurredAt) {}
