package br.com.brew.brassia.planning;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de domínio: uma ordem de produção foi liberada (BOP-002). Fato no
 * passado, consumível por outros módulos (ex.: brassagem, estoque).
 */
public record BrewOrderReleased(UUID breweryId, UUID orderId, String code, UUID recipeId, UUID assignedUserId,
        Instant occurredAt) {}
