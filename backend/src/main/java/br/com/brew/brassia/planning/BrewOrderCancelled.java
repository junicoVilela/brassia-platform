package br.com.brew.brassia.planning;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de domínio: uma ordem de produção foi cancelada (BOP-003). Consumível
 * por outros módulos — o estoque libera as reservas associadas (STK-003-B).
 */
public record BrewOrderCancelled(UUID breweryId, UUID orderId, String code, UUID actorId, String reason,
        Instant occurredAt) {}
